const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const morgan = require("morgan");
const rateLimit = require("express-rate-limit");
const compression = require("compression");
const { Pool } = require("pg");
require("dotenv").config();

const app = express();
const PORT = process.env.PORT || 8080;

// Database connection
const pool = new Pool({
  user: process.env.DB_USER || "onesec_user",
  host: process.env.DB_HOST || "localhost",
  database: process.env.DB_NAME || "onesec_analytics",
  password: process.env.DB_PASSWORD,
  port: process.env.DB_PORT || 5432,
});

// Middleware
app.use(helmet());
app.use(compression());
app.use(cors());
app.use(morgan("combined"));
app.use(express.json({ limit: "10mb" }));

// Rate limiting
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100, // limit each IP to 100 requests per windowMs
});
app.use("/api/", limiter);

// Health check endpoint
app.get("/api/health", (req, res) => {
  res.json({
    success: true,
    message: "Server is running",
    timestamp: new Date().toISOString(),
  });
});

// Single analytics data endpoint
app.post("/api/analytics/data", async (req, res) => {
  try {
    const deviceId = req.headers["x-device-id"];
    const analyticsData = req.body;

    if (!deviceId) {
      return res.status(400).json({
        success: false,
        message: "Device ID is required",
      });
    }

    // Insert data based on event type
    await insertAnalyticsData(deviceId, analyticsData);

    res.json({
      success: true,
      message: "Data received successfully",
      timestamp: new Date().toISOString(),
    });
  } catch (error) {
    console.error("Error processing analytics data:", error);
    res.status(500).json({
      success: false,
      message: "Internal server error",
    });
  }
});

// Batch analytics data endpoint
app.post("/api/analytics/batch", async (req, res) => {
  try {
    const { deviceId, userId, data, timestamp } = req.body;

    if (!deviceId || !data || !Array.isArray(data)) {
      return res.status(400).json({
        success: false,
        message: "Invalid batch data format",
      });
    }

    // Process each item in the batch
    for (const item of data) {
      await insertAnalyticsData(deviceId, item, userId);
    }

    res.json({
      success: true,
      message: `Batch of ${data.length} items processed successfully`,
      timestamp: new Date().toISOString(),
    });
  } catch (error) {
    console.error("Error processing batch data:", error);
    res.status(500).json({
      success: false,
      message: "Internal server error",
    });
  }
});

// Function to insert analytics data based on type
async function insertAnalyticsData(deviceId, data, userId = null) {
  const client = await pool.connect();

  try {
    switch (data.eventType) {
      case "app_session":
        // Check for duplicate session before inserting
        const sessionExists = await client.query(
          `
          SELECT id FROM app_sessions 
          WHERE device_id = $1 AND package_name = $2 
          AND session_start = $3 AND session_end = $4
        `,
          [deviceId, data.packageName, data.sessionStart, data.sessionEnd]
        );

        if (sessionExists.rows.length > 0) {
          console.log(
            `Duplicate app session detected for device ${deviceId}, package ${data.packageName}, skipping...`
          );
          return; // Skip insertion
        }

        await client.query(
          `
          INSERT INTO app_sessions (device_id, user_id, app_name, package_name, session_start, session_end, session_id, created_at)
          VALUES ($1, $2, $3, $4, $5, $6, $7, NOW())
        `,
          [
            deviceId,
            userId,
            data.appName,
            data.packageName,
            data.sessionStart,
            data.sessionEnd,
            data.sessionId,
          ]
        );
        break;

      case "intervention":
        // Check for duplicate intervention before inserting
        const interventionExists = await client.query(
          `
          SELECT id FROM interventions 
          WHERE device_id = $1 AND app_name = $2 
          AND intervention_start = $3 AND intervention_end = $4 AND intervention_type = $5
        `,
          [
            deviceId,
            data.appName,
            data.interventionStart,
            data.interventionEnd,
            data.interventionType,
          ]
        );

        if (interventionExists.rows.length > 0) {
          console.log(
            `Duplicate intervention detected for device ${deviceId}, app ${data.appName}, skipping...`
          );
          return; // Skip insertion
        }

        await client.query(
          `
          INSERT INTO interventions (device_id, user_id, intervention_start, intervention_end, app_name,
                                   intervention_type, video_duration, required_watch_time, button_clicked, intervention_id, created_at)
          VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, NOW())
        `,
          [
            deviceId,
            userId,
            data.interventionStart,
            data.interventionEnd,
            data.appName,
            data.interventionType,
            data.videoDuration,
            data.requiredWatchTime,
            data.buttonClicked,
            data.interventionId,
          ]
        );
        break;

      case "device_status":
        // For device status, we allow multiple entries but check for exact duplicates
        const deviceStatusExists = await client.query(
          `
          SELECT id FROM device_status 
          WHERE device_id = $1 AND battery_level = $2 AND is_charging = $3 
          AND connection_type = $4 AND connection_strength = $5 
          AND created_at > NOW() - INTERVAL '1 minute'
        `,
          [
            deviceId,
            data.batteryLevel,
            data.isCharging,
            data.connectionType,
            data.connectionStrength,
          ]
        );

        if (deviceStatusExists.rows.length > 0) {
          console.log(
            `Duplicate device status detected for device ${deviceId} within 1 minute, skipping...`
          );
          return; // Skip insertion
        }

        await client.query(
          `
          INSERT INTO device_status (device_id, user_id, battery_level, is_charging, connection_type,
                                   connection_strength, app_version, last_batch_sent, status_id, created_at)
          VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW())
        `,
          [
            deviceId,
            userId,
            data.batteryLevel,
            data.isCharging,
            data.connectionType,
            data.connectionStrength,
            data.appVersion,
            data.lastBatchSent,
            data.statusId,
          ]
        );
        break;

      case "daily_summary":
        // Check for duplicate daily summary before inserting
        const summaryExists = await client.query(
          `
          SELECT id FROM daily_summaries 
          WHERE device_id = $1 AND date = $2
        `,
          [deviceId, data.date]
        );

        if (summaryExists.rows.length > 0) {
          console.log(
            `Duplicate daily summary detected for device ${deviceId}, date ${data.date}, updating instead...`
          );
          // Update existing summary instead of creating duplicate
          await client.query(
            `
            UPDATE daily_summaries 
            SET total_screen_time = $3, app_totals = $4, summary_id = $5, created_at = NOW()
            WHERE device_id = $1 AND date = $2
          `,
            [
              deviceId,
              data.date,
              data.totalScreenTime,
              JSON.stringify(data.appTotals),
              data.summaryId,
            ]
          );
          return;
        }

        await client.query(
          `
          INSERT INTO daily_summaries (device_id, user_id, date, total_screen_time, app_totals, summary_id, created_at)
          VALUES ($1, $2, $3, $4, $5, $6, NOW())
        `,
          [
            deviceId,
            userId,
            data.date,
            data.totalScreenTime,
            JSON.stringify(data.appTotals),
            data.summaryId,
          ]
        );
        break;

      default:
        console.warn(`Unknown event type: ${data.eventType}`);
    }
  } catch (error) {
    // If it's a unique constraint violation, log it but don't crash
    if (error.code === "23505") {
      // PostgreSQL unique violation error code
      console.log(
        `Unique constraint violation prevented duplicate: ${error.detail}`
      );
      return; // Gracefully handle the duplicate
    }
    throw error; // Re-throw other errors
  } finally {
    client.release();
  }
}

// Error handling middleware
app.use((error, req, res, next) => {
  console.error("Unhandled error:", error);
  res.status(500).json({
    success: false,
    message: "Internal server error",
  });
});

// Start server
app.listen(PORT, () => {
  console.log(`OneSecClone Analytics Server running on port ${PORT}`);
  console.log(`Health check: http://localhost:${PORT}/api/health`);
});

// Graceful shutdown
process.on("SIGTERM", () => {
  console.log("SIGTERM received, shutting down gracefully");
  pool.end(() => {
    process.exit(0);
  });
});
