-- Database schema for OneSecClone Analytics
-- Run this script after creating the database

-- App Sessions Table
CREATE TABLE app_sessions (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255),
    app_name VARCHAR(255) NOT NULL,
    package_name VARCHAR(255) NOT NULL,
    session_start TIMESTAMP WITH TIME ZONE NOT NULL,
    session_end TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Interventions Table
CREATE TABLE interventions (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255),
    intervention_start TIMESTAMP WITH TIME ZONE NOT NULL,
    intervention_end TIMESTAMP WITH TIME ZONE NOT NULL,
    app_name VARCHAR(255) NOT NULL,
    intervention_type VARCHAR(100) NOT NULL,
    video_duration INTEGER,
    required_watch_time INTEGER,
    button_clicked VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Device Status Table
CREATE TABLE device_status (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255),
    battery_level INTEGER NOT NULL,
    is_charging BOOLEAN NOT NULL,
    connection_type VARCHAR(50) NOT NULL,
    connection_strength VARCHAR(50) NOT NULL,
    app_version VARCHAR(50) NOT NULL,
    last_batch_sent TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Daily Summaries Table
CREATE TABLE daily_summaries (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255),
    date DATE NOT NULL,
    total_screen_time INTEGER NOT NULL,
    app_totals JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create indexes for better performance
CREATE INDEX idx_app_sessions_device_id ON app_sessions(device_id);
CREATE INDEX idx_app_sessions_created_at ON app_sessions(created_at);
CREATE INDEX idx_interventions_device_id ON interventions(device_id);
CREATE INDEX idx_interventions_created_at ON interventions(created_at);
CREATE INDEX idx_device_status_device_id ON device_status(device_id);
CREATE INDEX idx_device_status_created_at ON device_status(created_at);
CREATE INDEX idx_daily_summaries_device_id ON daily_summaries(device_id);
CREATE INDEX idx_daily_summaries_date ON daily_summaries(date);

-- Add unique constraints to prevent duplicate data
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

-- Grant permissions to the onesec_user
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO onesec_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO onesec_user;
