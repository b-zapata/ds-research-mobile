# OneSecClone Database Schema Documentation

## Overview

This document describes the PostgreSQL database schema for the OneSecClone analytics server. The database tracks user app usage, interventions, device status, and daily summaries.

**Database Name**: `onesec_analytics`  
**Database User**: `onesec_user`  
**Last Updated**: August 2, 2025  
**Duplicate Prevention**: ✅ Enabled (Added Aug 2, 2025)

## Current Schema (4 Tables)

### 1. app_sessions

Tracks individual app usage sessions.

| Column          | Type                     | Nullable | Description                                       |
| --------------- | ------------------------ | -------- | ------------------------------------------------- |
| `id`            | SERIAL PRIMARY KEY       | NO       | Auto-incrementing unique identifier               |
| `device_id`     | VARCHAR(255)             | NO       | Device identifier from Android                    |
| `user_id`       | VARCHAR(255)             | YES      | Optional user identifier                          |
| `app_name`      | VARCHAR(255)             | NO       | Human-readable app name                           |
| `package_name`  | VARCHAR(255)             | NO       | Android package name                              |
| `session_start` | TIMESTAMP WITH TIME ZONE | NO       | When the app session started                      |
| `session_end`   | TIMESTAMP WITH TIME ZONE | NO       | When the app session ended                        |
| `session_id`    | VARCHAR(255)             | YES      | Client-generated unique session identifier (UUID) |
| `created_at`    | TIMESTAMP WITH TIME ZONE | NO       | Record creation timestamp (defaults to NOW())     |

**Indexes**:

- Primary key on `id`
- Index on `device_id` for device-based queries
- Index on `created_at` for time-based queries
- Index on `session_id` for UUID-based queries

**Duplicate Prevention**:

- **Unique Constraint**: `unique_app_session` on `(device_id, package_name, session_start, session_end)`
- **Server-Side Check**: Queries existing sessions before insertion
- **Client UUID**: Each session has unique `session_id` for traceability

### 2. interventions

Tracks intervention events (video interruptions when users try to open apps).

| Column                | Type                     | Nullable | Description                                            |
| --------------------- | ------------------------ | -------- | ------------------------------------------------------ |
| `id`                  | SERIAL PRIMARY KEY       | NO       | Auto-incrementing unique identifier                    |
| `device_id`           | VARCHAR(255)             | NO       | Device identifier from Android                         |
| `user_id`             | VARCHAR(255)             | YES      | Optional user identifier                               |
| `intervention_start`  | TIMESTAMP WITH TIME ZONE | NO       | When intervention began                                |
| `intervention_end`    | TIMESTAMP WITH TIME ZONE | NO       | When intervention ended                                |
| `app_name`            | VARCHAR(255)             | NO       | App that triggered the intervention                    |
| `intervention_type`   | VARCHAR(100)             | NO       | Type of intervention (e.g., "video")                   |
| `video_duration`      | INTEGER                  | YES      | Total video length in seconds                          |
| `required_watch_time` | INTEGER                  | YES      | Minimum required watch time in seconds                 |
| `button_clicked`      | VARCHAR(100)             | NO       | Which button user clicked (e.g., "continue", "cancel") |
| `intervention_id`     | VARCHAR(255)             | YES      | Client-generated unique intervention identifier (UUID) |
| `created_at`          | TIMESTAMP WITH TIME ZONE | NO       | Record creation timestamp (defaults to NOW())          |

**Indexes**:

- Primary key on `id`
- Index on `device_id` for device-based queries
- Index on `intervention_id` for UUID-based queries

**Duplicate Prevention**:

- **Unique Constraint**: `unique_intervention` on `(device_id, app_name, intervention_start, intervention_end, intervention_type)`
- **Server-Side Check**: Queries existing interventions before insertion
- **Client UUID**: Each intervention has unique `intervention_id` for traceability
- Index on `created_at` for time-based queries

### 3. device_status

Tracks device health and connectivity information.

| Column                | Type                     | Nullable | Description                                      |
| --------------------- | ------------------------ | -------- | ------------------------------------------------ |
| `id`                  | SERIAL PRIMARY KEY       | NO       | Auto-incrementing unique identifier              |
| `device_id`           | VARCHAR(255)             | NO       | Device identifier from Android                   |
| `user_id`             | VARCHAR(255)             | YES      | Optional user identifier                         |
| `battery_level`       | INTEGER                  | NO       | Battery percentage (0-100)                       |
| `is_charging`         | BOOLEAN                  | NO       | Whether device is currently charging             |
| `connection_type`     | VARCHAR(50)              | NO       | Network type (e.g., "WiFi", "Mobile")            |
| `connection_strength` | VARCHAR(50)              | NO       | Signal strength indicator                        |
| `app_version`         | VARCHAR(50)              | NO       | OneSecClone app version                          |
| `last_batch_sent`     | TIMESTAMP WITH TIME ZONE | YES      | Last time data was uploaded                      |
| `status_id`           | VARCHAR(255)             | YES      | Client-generated unique status identifier (UUID) |
| `created_at`          | TIMESTAMP WITH TIME ZONE | NO       | Record creation timestamp (defaults to NOW())    |

**Indexes**:

- Primary key on `id`
- Index on `device_id` for device-based queries
- Index on `created_at` for time-based queries
- Index on `status_id` for UUID-based queries

**Duplicate Prevention**:

- **Unique Constraint**: `unique_device_status` on `(device_id, battery_level, is_charging, connection_type, connection_strength, created_at)`
- **Server-Side Check**: Prevents identical readings within 1-minute window
- **Client UUID**: Each status reading has unique `status_id` for traceability

### 4. daily_summaries

Daily aggregated usage statistics per device.

| Column              | Type                     | Nullable | Description                                       |
| ------------------- | ------------------------ | -------- | ------------------------------------------------- |
| `id`                | SERIAL PRIMARY KEY       | NO       | Auto-incrementing unique identifier               |
| `device_id`         | VARCHAR(255)             | NO       | Device identifier from Android                    |
| `user_id`           | VARCHAR(255)             | YES      | Optional user identifier                          |
| `date`              | DATE                     | NO       | Summary date                                      |
| `total_screen_time` | INTEGER                  | NO       | Total daily screen time in seconds                |
| `app_totals`        | JSONB                    | NO       | JSON object with per-app usage data               |
| `summary_id`        | VARCHAR(255)             | YES      | Client-generated unique summary identifier (UUID) |
| `created_at`        | TIMESTAMP WITH TIME ZONE | NO       | Record creation timestamp (defaults to NOW())     |

**Indexes**:

- Primary key on `id`
- Index on `device_id` for device-based queries
- Index on `date` for date-based queries
- Index on `summary_id` for UUID-based queries

**Duplicate Prevention**:

- **Unique Constraint**: `unique_daily_summary` on `(device_id, date)` - ensures one summary per device per day
- **Server-Side Logic**: Updates existing summary instead of creating duplicates
- **Client UUID**: Each summary has unique `summary_id` for traceability

## Duplicate Prevention System (Added Aug 2, 2025)

### Overview

A comprehensive three-layer duplicate prevention system protects research data integrity:

1. **Database Constraints** - PostgreSQL unique constraints prevent exact duplicates
2. **Server-Side Detection** - Pre-insertion checks with graceful handling
3. **Client-Side UUIDs** - Unique identifiers for better traceability and debugging

### Unique Constraints

- **app_sessions**: `(device_id, package_name, session_start, session_end)`
- **interventions**: `(device_id, app_name, intervention_start, intervention_end, intervention_type)`
- **device_status**: `(device_id, battery_level, is_charging, connection_type, connection_strength, created_at)`
- **daily_summaries**: `(device_id, date)`

### Server-Side Logic

- Queries for existing records before insertion
- Logs duplicate attempts but continues processing gracefully
- Special handling for daily summaries (updates instead of duplicating)
- Time-window protection for device status (1-minute duplicate prevention)

### Monitoring Duplicates

```sql
-- Check for any constraint violations in logs
SELECT schemaname, tablename, attname, n_distinct
FROM pg_stats
WHERE tablename IN ('app_sessions', 'interventions', 'device_status', 'daily_summaries');

-- Sample queries to verify data integrity
SELECT device_id, COUNT(*) as session_count
FROM app_sessions
GROUP BY device_id
ORDER BY session_count DESC;
```

## Database Setup Instructions

### 1. Create Database and User

```sql
CREATE DATABASE onesec_analytics;
CREATE USER onesec_user WITH ENCRYPTED PASSWORD 'your_password_here';
GRANT ALL PRIVILEGES ON DATABASE onesec_analytics TO onesec_user;
ALTER USER onesec_user CREATEDB;
```

### 2. Apply Schema

**For New Databases:**

```bash
PGPASSWORD=your_password psql -h localhost -U onesec_user -d onesec_analytics -f database_schema.sql
```

**For Existing Databases (Migration):**

```bash
# Apply unique constraints
PGPASSWORD=your_password psql -h localhost -U onesec_user -d onesec_analytics -f migrations/001_add_unique_constraints.sql

# Add UUID columns
PGPASSWORD=your_password psql -h localhost -U onesec_user -d onesec_analytics -f migrations/002_add_client_ids.sql
```

### 3. Verify Setup

```sql
-- Connect to database
PGPASSWORD=your_password psql -h localhost -U onesec_user -d onesec_analytics

-- List tables
\dt

-- Check table structures with new columns
\d+ app_sessions
\d+ interventions
\d+ device_status
\d+ daily_summaries

-- Verify unique constraints
SELECT conname, contype
FROM pg_constraint
WHERE contype = 'u' AND conrelid IN (
    SELECT oid FROM pg_class WHERE relname IN ('app_sessions', 'interventions', 'device_status', 'daily_summaries')
);
```

\d+ interventions
\d+ device_status
\d+ daily_summaries

```

## Environment Configuration

Create a `.env` file with:
```

DB_HOST=localhost
DB_PORT=5432
DB_NAME=onesec_analytics
DB_USER=onesec_user
DB_PASSWORD=your_secure_password
PORT=8080
NODE_ENV=production

````

## Data Verification

Use the provided verification script:
```bash
node verify_data_flow.js
````

Available commands:

- `node verify_data_flow.js` - Basic connectivity and structure check
- `node verify_data_flow.js --monitor` - Live data monitoring
- `node verify_data_flow.js --stats` - Detailed statistics

## Security Notes

1. **Password Security**: Use strong passwords in production
2. **Network Access**: Restrict database access to localhost or trusted IPs
3. **Regular Backups**: Implement automated backup strategy
4. **SSL/TLS**: Enable SSL connections in production environments

## Change Log

- **August 1, 2025**: Removed `app_taps` table from schema
- **Initial**: Created 5-table schema for analytics tracking

## API Endpoints

The server provides these endpoints for data collection:

- `POST /api/analytics/data` - Bulk analytics data upload
- `GET /api/health` - Server health check

All data should be sent with the `x-device-id` header for device identification.
