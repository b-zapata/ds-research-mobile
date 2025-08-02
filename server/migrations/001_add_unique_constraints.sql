-- Migration: Add unique constraints to prevent duplicate data
-- Run this on your existing database to add duplicate prevention
-- Date: August 2, 2025

-- Add unique constraints to prevent duplicate data
-- Note: This will fail if you already have duplicate data in your tables
-- In that case, clean up duplicates first before running this migration

BEGIN;

-- Prevent exact duplicate app sessions
ALTER TABLE app_sessions 
ADD CONSTRAINT unique_app_session 
UNIQUE (device_id, package_name, session_start, session_end);

-- Prevent exact duplicate interventions
ALTER TABLE interventions 
ADD CONSTRAINT unique_intervention 
UNIQUE (device_id, app_name, intervention_start, intervention_end, intervention_type);

-- Prevent duplicate daily summaries for same device and date
ALTER TABLE daily_summaries 
ADD CONSTRAINT unique_daily_summary 
UNIQUE (device_id, date);

-- Device status can have multiple entries per device (for tracking over time)
-- but prevent exact duplicates at the same timestamp
ALTER TABLE device_status 
ADD CONSTRAINT unique_device_status 
UNIQUE (device_id, battery_level, is_charging, connection_type, connection_strength, created_at);

COMMIT;

-- Optional: Check for existing duplicates before applying constraints
-- Uncomment these queries to check if you have existing duplicates:

/*
-- Check for duplicate app sessions
SELECT device_id, package_name, session_start, session_end, COUNT(*) 
FROM app_sessions 
GROUP BY device_id, package_name, session_start, session_end 
HAVING COUNT(*) > 1;

-- Check for duplicate interventions
SELECT device_id, app_name, intervention_start, intervention_end, intervention_type, COUNT(*) 
FROM interventions 
GROUP BY device_id, app_name, intervention_start, intervention_end, intervention_type 
HAVING COUNT(*) > 1;

-- Check for duplicate daily summaries
SELECT device_id, date, COUNT(*) 
FROM daily_summaries 
GROUP BY device_id, date 
HAVING COUNT(*) > 1;

-- Check for duplicate device status entries
SELECT device_id, battery_level, is_charging, connection_type, connection_strength, created_at, COUNT(*) 
FROM device_status 
GROUP BY device_id, battery_level, is_charging, connection_type, connection_strength, created_at 
HAVING COUNT(*) > 1;
*/
