# Database Migrations

This folder contains database migration scripts for the OneSecClone analytics server.

## Migration Files

### 001_add_unique_constraints.sql

**Date**: August 2, 2025  
**Purpose**: Add unique constraints to prevent duplicate data  
**Tables Modified**: All (app_sessions, interventions, device_status, daily_summaries)

**What it does**:

- Adds unique constraints to prevent exact duplicate records
- Creates composite unique keys based on meaningful data combinations
- Prevents research data corruption from duplicate submissions

**Before running**: Check for existing duplicates using the commented queries in the file

### 002_add_client_ids.sql

**Date**: August 2, 2025  
**Purpose**: Add client-generated UUID columns for better traceability  
**Tables Modified**: All (app_sessions, interventions, device_status, daily_summaries)

**What it does**:

- Adds `session_id`, `intervention_id`, `status_id`, `summary_id` columns
- Creates indexes on the new UUID columns
- Enables better debugging and data tracking

## Running Migrations

### For New Databases

Use the main `database_schema.sql` file which includes all constraints and columns.

### For Existing Databases

Run migrations in order:

```bash
# Apply unique constraints first
PGPASSWORD=your_password psql -h localhost -U onesec_user -d onesec_analytics -f 001_add_unique_constraints.sql

# Then add UUID columns
PGPASSWORD=your_password psql -h localhost -U onesec_user -d onesec_analytics -f 002_add_client_ids.sql
```

## Verification

After running migrations, verify with:

```sql
-- Check constraints were added
SELECT conname, contype
FROM pg_constraint
WHERE contype = 'u' AND conrelid IN (
    SELECT oid FROM pg_class WHERE relname IN ('app_sessions', 'interventions', 'device_status', 'daily_summaries')
);

-- Check new columns exist
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_name IN ('app_sessions', 'interventions', 'device_status', 'daily_summaries')
AND column_name LIKE '%_id'
ORDER BY table_name, column_name;
```

## Rollback

To rollback these migrations:

```sql
-- Remove unique constraints
ALTER TABLE app_sessions DROP CONSTRAINT IF EXISTS unique_app_session;
ALTER TABLE interventions DROP CONSTRAINT IF EXISTS unique_intervention;
ALTER TABLE daily_summaries DROP CONSTRAINT IF EXISTS unique_daily_summary;
ALTER TABLE device_status DROP CONSTRAINT IF EXISTS unique_device_status;

-- Remove UUID columns (optional)
ALTER TABLE app_sessions DROP COLUMN IF EXISTS session_id;
ALTER TABLE interventions DROP COLUMN IF EXISTS intervention_id;
ALTER TABLE device_status DROP COLUMN IF EXISTS status_id;
ALTER TABLE daily_summaries DROP COLUMN IF EXISTS summary_id;
```

## Notes

- **Migration 001** may fail if you have existing duplicate data. Clean up duplicates first.
- **Migration 002** is safe to run on any existing database.
- Always backup your database before running migrations.
- Test migrations on a development database first.
