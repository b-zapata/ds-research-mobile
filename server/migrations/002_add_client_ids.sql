-- Migration: Add client-generated unique ID columns
-- Run this after the previous migration to add unique ID tracking
-- Date: August 2, 2025

BEGIN;

-- Add unique ID columns to track client-generated identifiers
-- These will help with additional duplicate detection and data tracing

-- Add session_id to app_sessions table
ALTER TABLE app_sessions 
ADD COLUMN session_id VARCHAR(255);

-- Add intervention_id to interventions table
ALTER TABLE interventions 
ADD COLUMN intervention_id VARCHAR(255);

-- Add status_id to device_status table
ALTER TABLE device_status 
ADD COLUMN status_id VARCHAR(255);

-- Add summary_id to daily_summaries table
ALTER TABLE daily_summaries 
ADD COLUMN summary_id VARCHAR(255);

-- Create unique indexes on the new ID columns (optional but recommended)
CREATE INDEX idx_app_sessions_session_id ON app_sessions(session_id);
CREATE INDEX idx_interventions_intervention_id ON interventions(intervention_id);
CREATE INDEX idx_device_status_status_id ON device_status(status_id);
CREATE INDEX idx_daily_summaries_summary_id ON daily_summaries(summary_id);

COMMIT;
