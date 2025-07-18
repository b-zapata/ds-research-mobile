const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const rateLimit = require('express-rate-limit');
const compression = require('compression');
const { Pool } = require('pg');
require('dotenv').config();

const app = express();
const PORT = process.env.PORT || 8080;

// Database connection
const pool = new Pool({
  user: process.env.DB_USER || 'onesec_user',
  host: process.env.DB_HOST || 'localhost',
  database: process.env.DB_NAME || 'onesec_analytics',
  password: process.env.DB_PASSWORD,
  port: process.env.DB_PORT || 5432,
});

// Middleware
app.use(helmet());
app.use(compression());
app.use(cors());
app.use(morgan('combined'));
app.use(express.json({ limit: '10mb' }));

// Rate limiting
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100 // limit each IP to 100 requests per windowMs
});
app.use('/api/', limiter);

// Health check endpoint
app.get('/api/health', (req, res) => {
  res.json({
    success: true,
    message: 'Server is running',
    timestamp: new Date().toISOString()
  });
});

// Single analytics data endpoint
app.post('/api/analytics/data', async (req, res) => {
  try {
    const deviceId = req.headers['x-device-id'];
    const analyticsData = req.body;

    if (!deviceId) {
      return res.status(400).json({
        success: false,
        message: 'Device ID is required'
      });
    }

    // Insert data based on event type
    await insertAnalyticsData(deviceId, analyticsData);

    res.json({
      success: true,
      message: 'Data received successfully',
      timestamp: new Date().toISOString()
    });
  } catch (error) {
    console.error('Error processing analytics data:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error'
    });
  }
});

// Batch analytics data endpoint
app.post('/api/analytics/batch', async (req, res) => {
  try {
    const { deviceId, userId, data, timestamp } = req.body;

    if (!deviceId || !data || !Array.isArray(data)) {
      return res.status(400).json({
        success: false,
        message: 'Invalid batch data format'
      });
    }

    // Process each item in the batch
    for (const item of data) {
      await insertAnalyticsData(deviceId, item, userId);
    }

    res.json({
      success: true,
      message: `Batch of ${data.length} items processed successfully`,
      timestamp: new Date().toISOString()
    });
  } catch (error) {
    console.error('Error processing batch data:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error'
    });
  }
});

// Function to insert analytics data based on type
async function insertAnalyticsData(deviceId, data, userId = null) {
  const client = await pool.connect();

  try {
    switch (data.eventType) {
      case 'app_session':
        await client.query(`
          INSERT INTO app_sessions (device_id, user_id, app_name, package_name, session_start, session_end, created_at)
          VALUES ($1, $2, $3, $4, $5, $6, NOW())
        `, [deviceId, userId, data.appName, data.packageName, data.sessionStart, data.sessionEnd]);
        break;

      case 'app_tap':
        await client.query(`
          INSERT INTO app_taps (device_id, user_id, timestamp, app_name, package_name, created_at)
          VALUES ($1, $2, $3, $4, $5, NOW())
        `, [deviceId, userId, data.timestamp, data.appName, data.packageName]);
        break;

      case 'intervention':
        await client.query(`
          INSERT INTO interventions (device_id, user_id, intervention_start, intervention_end, app_name,
                                   intervention_type, video_duration, required_watch_time, button_clicked, created_at)
          VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW())
        `, [deviceId, userId, data.interventionStart, data.interventionEnd, data.appName,
            data.interventionType, data.videoDuration, data.requiredWatchTime, data.buttonClicked]);
        break;

      case 'device_status':
        await client.query(`
          INSERT INTO device_status (device_id, user_id, battery_level, is_charging, connection_type,
                                   connection_strength, app_version, last_batch_sent, created_at)
          VALUES ($1, $2, $3, $4, $5, $6, $7, $8, NOW())
        `, [deviceId, userId, data.batteryLevel, data.isCharging, data.connectionType,
            data.connectionStrength, data.appVersion, data.lastBatchSent]);
        break;

      case 'daily_summary':
        await client.query(`
          INSERT INTO daily_summaries (device_id, user_id, date, total_screen_time, app_totals, created_at)
          VALUES ($1, $2, $3, $4, $5, NOW())
        `, [deviceId, userId, data.date, data.totalScreenTime, JSON.stringify(data.appTotals)]);
        break;

      default:
        console.warn(`Unknown event type: ${data.eventType}`);
    }
  } finally {
    client.release();
  }
}

// Error handling middleware
app.use((error, req, res, next) => {
  console.error('Unhandled error:', error);
  res.status(500).json({
    success: false,
    message: 'Internal server error'
  });
});

// Start server
app.listen(PORT, () => {
  console.log(`OneSecClone Analytics Server running on port ${PORT}`);
  console.log(`Health check: http://localhost:${PORT}/api/health`);
});

// Graceful shutdown
process.on('SIGTERM', () => {
  console.log('SIGTERM received, shutting down gracefully');
  pool.end(() => {
    process.exit(0);
  });
});
