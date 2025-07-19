#!/usr/bin/env node

const { Pool } = require('pg');
const express = require('express');
require('dotenv').config();

// Database connection
const pool = new Pool({
  user: process.env.DB_USER || 'onesec_user',
  host: process.env.DB_HOST || 'localhost',
  database: process.env.DB_NAME || 'onesec_analytics',
  password: process.env.DB_PASSWORD,
  port: process.env.DB_PORT || 5432,
});

class DataVerificationService {
  constructor() {
    this.startTime = new Date();
    this.receivedDataCount = 0;
    this.errorCount = 0;
  }

  async verifyDatabaseConnection() {
    try {
      const client = await pool.connect();
      const result = await client.query('SELECT NOW() as current_time');
      console.log('✅ Database connection successful');
      console.log(`   Connected at: ${result.rows[0].current_time}`);
      client.release();
      return true;
    } catch (error) {
      console.error('❌ Database connection failed:', error.message);
      return false;
    }
  }

  async verifyTableStructures() {
    const tables = ['app_sessions', 'app_taps', 'interventions', 'device_status', 'daily_summaries'];

    for (const table of tables) {
      try {
        const result = await pool.query(`
          SELECT column_name, data_type, is_nullable
          FROM information_schema.columns
          WHERE table_name = $1
          ORDER BY ordinal_position
        `, [table]);

        console.log(`✅ Table '${table}' exists with ${result.rows.length} columns`);
      } catch (error) {
        console.error(`❌ Table '${table}' verification failed:`, error.message);
      }
    }
  }

  async getDataCounts() {
    const tables = ['app_sessions', 'app_taps', 'interventions', 'device_status', 'daily_summaries'];

    console.log('\n📊 Current Data Counts:');
    for (const table of tables) {
      try {
        const result = await pool.query(`SELECT COUNT(*) as count FROM ${table}`);
        const count = result.rows[0].count;
        console.log(`   ${table}: ${count} records`);

        if (count > 0) {
          const recent = await pool.query(`
            SELECT created_at FROM ${table}
            ORDER BY created_at DESC LIMIT 1
          `);
          console.log(`     Latest: ${recent.rows[0].created_at}`);
        }
      } catch (error) {
        console.error(`   ${table}: Error - ${error.message}`);
      }
    }
  }

  async getRecentActivity(minutes = 30) {
    console.log(`\n🕒 Activity in last ${minutes} minutes:`);
    const tables = ['app_sessions', 'app_taps', 'interventions', 'device_status', 'daily_summaries'];

    for (const table of tables) {
      try {
        const result = await pool.query(`
          SELECT COUNT(*) as count
          FROM ${table}
          WHERE created_at > NOW() - INTERVAL '${minutes} minutes'
        `);
        const count = result.rows[0].count;
        if (count > 0) {
          console.log(`   ${table}: ${count} new records`);
        }
      } catch (error) {
        console.error(`   ${table}: Error - ${error.message}`);
      }
    }
  }

  async getUniqueDevices() {
    console.log('\n📱 Device Activity:');
    const tables = ['app_sessions', 'app_taps', 'interventions', 'device_status', 'daily_summaries'];

    for (const table of tables) {
      try {
        const result = await pool.query(`
          SELECT COUNT(DISTINCT device_id) as unique_devices
          FROM ${table}
        `);
        const count = result.rows[0].unique_devices;
        if (count > 0) {
          console.log(`   ${table}: ${count} unique devices`);
        }
      } catch (error) {
        console.error(`   ${table}: Error - ${error.message}`);
      }
    }
  }

  async monitorLiveData(intervalSeconds = 30) {
    console.log(`\n🔄 Starting live monitoring (checking every ${intervalSeconds}s)...`);
    console.log('Press Ctrl+C to stop\n');

    const previousCounts = {};

    setInterval(async () => {
      const tables = ['app_sessions', 'app_taps', 'interventions', 'device_status', 'daily_summaries'];
      let hasNewData = false;

      for (const table of tables) {
        try {
          const result = await pool.query(`SELECT COUNT(*) as count FROM ${table}`);
          const currentCount = parseInt(result.rows[0].count);
          const previousCount = previousCounts[table] || 0;

          if (currentCount > previousCount) {
            const newRecords = currentCount - previousCount;
            console.log(`📈 ${new Date().toISOString()} - ${table}: +${newRecords} records (total: ${currentCount})`);
            hasNewData = true;
          }

          previousCounts[table] = currentCount;
        } catch (error) {
          console.error(`❌ Error monitoring ${table}:`, error.message);
        }
      }

      if (!hasNewData) {
        console.log(`⏱️  ${new Date().toISOString()} - No new data received`);
      }
    }, intervalSeconds * 1000);
  }

  async runFullVerification() {
    console.log('🔍 OneSecClone Data Flow Verification\n');
    console.log('=' .repeat(50));

    const dbConnected = await this.verifyDatabaseConnection();
    if (!dbConnected) {
      console.log('\n❌ Cannot proceed without database connection');
      process.exit(1);
    }

    await this.verifyTableStructures();
    await this.getDataCounts();
    await this.getRecentActivity();
    await this.getUniqueDevices();

    console.log('\n' + '='.repeat(50));
    console.log('✅ Verification complete!');
    console.log('\nTo monitor live data flow, run:');
    console.log('node verify_data_flow.js --monitor');
  }
}

// CLI interface
const args = process.argv.slice(2);
const verifier = new DataVerificationService();

if (args.includes('--monitor') || args.includes('-m')) {
  verifier.monitorLiveData();
} else if (args.includes('--help') || args.includes('-h')) {
  console.log(`
OneSecClone Data Verification Tool

Usage:
  node verify_data_flow.js              # Run full verification
  node verify_data_flow.js --monitor    # Monitor live data flow
  node verify_data_flow.js --help       # Show this help

Options:
  --monitor, -m    Monitor incoming data in real-time
  --help, -h       Show help information
  `);
} else {
  verifier.runFullVerification();
}

// Graceful shutdown
process.on('SIGINT', () => {
  console.log('\n\n👋 Shutting down gracefully...');
  pool.end(() => {
    process.exit(0);
  });
});
