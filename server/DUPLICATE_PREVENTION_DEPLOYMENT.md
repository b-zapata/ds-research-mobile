# Duplicate Prevention - EC2 Deployment Guide

This guide walks you through deploying the NEW duplicate prevention system to your existing EC2 instance.

## 📋 Pre-Deployment Checklist

Before proceeding, ensure you have:

- ✅ SSH access to your EC2 instance (44.247.94.119)
- ✅ Your EC2 private key file: `C:\Users\bzapa\OneDrive\Desktop\Research\pem_files\ds_research.pem`
- ✅ Database credentials for your PostgreSQL instance
- ✅ Current server.js backup (we'll create this)

## 🎯 What We're Deploying

### New Features Being Added:

1. **Database Unique Constraints** - Prevents exact duplicates at PostgreSQL level
2. **Server-Side Duplicate Detection** - Pre-insertion checks with graceful handling
3. **Client-Generated UUIDs** - Better traceability and debugging
4. **Enhanced Logging** - Monitor duplicate prevention in real-time

### Files Being Updated:

- `server.js` - Enhanced duplicate detection logic
- `database_schema.sql` - Added unique constraints and UUID columns
- `migrations/` - New migration scripts for existing databases
- `DATABASE_SCHEMA.md` - Updated documentation

## 🚀 Step-by-Step Deployment

### Step 1: Prepare Local Files for Upload

From your Windows machine:

```cmd
cd C:\Users\bzapa\AndroidStudioProjects\OneSecClone\server

# Create deployment package with new files
tar -czf duplicate-prevention.tar.gz server.js database_schema.sql migrations/ DATABASE_SCHEMA.md
```

### Step 2: Connect to EC2

```cmd
ssh -i "C:\Users\bzapa\OneDrive\Desktop\Research\pem_files\ds_research.pem" ec2-user@44.247.94.119
```

### Step 3: Upload New Files

From your **local machine** (new command prompt):

```cmd
# Upload the deployment package
scp -i "C:\Users\bzapa\OneDrive\Desktop\Research\pem_files\ds_research.pem" duplicate-prevention.tar.gz ec2-user@44.247.94.119:~/
```

### Step 4: Backup Current Server (On EC2)

```bash
# Navigate to your server directory
cd ~/onesec-server  # or wherever your server is located

# Create backup with timestamp
sudo cp server.js server.js.backup.$(date +%Y%m%d_%H%M%S)

# Backup database (just in case)
sudo -u postgres pg_dump onesec_analytics > db_backup_$(date +%Y%m%d_%H%M%S).sql

# Extract new files
cd ~
tar -xzf duplicate-prevention.tar.gz
```

### Step 5: Apply Database Migrations

```bash
# First, let's check for any existing duplicates
sudo -u postgres psql -d onesec_analytics -c "
SELECT device_id, package_name, session_start, session_end, COUNT(*)
FROM app_sessions
GROUP BY device_id, package_name, session_start, session_end
HAVING COUNT(*) > 1;
"

# Apply unique constraints migration
sudo -u postgres psql -d onesec_analytics -f migrations/001_add_unique_constraints.sql

# Add UUID columns
sudo -u postgres psql -d onesec_analytics -f migrations/002_add_client_ids.sql

# Verify constraints were added
sudo -u postgres psql -d onesec_analytics -c "
SELECT conname, contype
FROM pg_constraint
WHERE contype = 'u' AND conrelid IN (
    SELECT oid FROM pg_class WHERE relname IN ('app_sessions', 'interventions', 'device_status', 'daily_summaries')
);
"
```

### Step 6: Update Server Code

```bash
# Copy new server.js to your server directory
cp server.js ~/onesec-server/

# Test the new server file
cd ~/onesec-server
node -c server.js
```

### Step 7: Restart Server

```bash
# Find and stop current server process
sudo pkill -f "node server.js"

# Start new server with enhanced duplicate prevention
nohup node server.js > server.log 2>&1 &

# Verify it's running
curl http://localhost:8080/api/health
```

### Step 8: Test Duplicate Prevention

```bash
# Test 1: Try to create duplicate via database (should fail gracefully)
sudo -u postgres psql -d onesec_analytics -c "
INSERT INTO app_sessions (device_id, package_name, session_start, session_end)
VALUES ('test-device', 'com.example.app', '2025-08-02 10:00:00+00', '2025-08-02 10:05:00+00');
"

# Try the same insert again (should see constraint violation)
sudo -u postgres psql -d onesec_analytics -c "
INSERT INTO app_sessions (device_id, package_name, session_start, session_end)
VALUES ('test-device', 'com.example.app', '2025-08-02 10:00:00+00', '2025-08-02 10:05:00+00');
"

# Test 2: Send duplicate data via API
cat > test_duplicate.json << EOF
{
  "deviceId": "test-device-123",
  "data": [
    {
      "eventType": "app_session",
      "sessionId": "550e8400-e29b-41d4-a716-446655440000",
      "appName": "Test App",
      "packageName": "com.test.app",
      "sessionStart": "2025-08-02T15:00:00Z",
      "sessionEnd": "2025-08-02T15:05:00Z"
    }
  ],
  "timestamp": "2025-08-02T15:05:00Z"
}
EOF

# Send the same data twice
curl -X POST http://localhost:8080/api/analytics/batch \
  -H "Content-Type: application/json" \
  -d @test_duplicate.json

curl -X POST http://localhost:8080/api/analytics/batch \
  -H "Content-Type: application/json" \
  -d @test_duplicate.json

# Check logs for duplicate detection
tail -20 server.log | grep -i "duplicate"
```

## 🔍 Verification Checklist

### ✅ Server Health

```bash
# Health endpoint responds
curl http://44.247.94.119:8080/api/health

# Server logs show no errors
tail -20 server.log
```

### ✅ Database Constraints Active

```bash
# Check constraints exist
sudo -u postgres psql -d onesec_analytics -c "\d+ app_sessions"

# Verify UUID columns added
sudo -u postgres psql -d onesec_analytics -c "
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'app_sessions' AND column_name LIKE '%_id';
"
```

### ✅ Duplicate Prevention Working

```bash
# Monitor for duplicate detection logs
tail -f server.log | grep -i "duplicate"

# Check database record counts haven't exploded
sudo -u postgres psql -d onesec_analytics -c "
SELECT
    'app_sessions' as table_name, COUNT(*) as records FROM app_sessions
UNION ALL
SELECT
    'interventions', COUNT(*) FROM interventions
UNION ALL
SELECT
    'device_status', COUNT(*) FROM device_status
UNION ALL
SELECT
    'daily_summaries', COUNT(*) FROM daily_summaries;
"
```

## 📊 Monitoring Setup

### Create Monitoring Script

```bash
cat > monitor_duplicates.sh << 'EOF'
#!/bin/bash
echo "=== OneSecClone Duplicate Prevention Monitor ==="
echo "Date: $(date)"
echo ""

echo "🔍 Recent duplicate detections:"
tail -100 server.log | grep -i "duplicate" | tail -5

echo ""
echo "📊 Database record counts:"
sudo -u postgres psql -d onesec_analytics -c "
SELECT
    'app_sessions' as table_name, COUNT(*) as total_records,
    COUNT(DISTINCT device_id) as unique_devices
FROM app_sessions
UNION ALL
SELECT
    'interventions', COUNT(*), COUNT(DISTINCT device_id)
FROM interventions;"

echo ""
echo "⚠️  Recent constraint violations:"
sudo tail -50 /var/log/postgresql/postgresql-*.log 2>/dev/null | grep -i "duplicate key\|unique constraint" | tail -3 || echo "No PostgreSQL logs accessible"

echo ""
echo "🚀 Server status:"
if pgrep -f "node server.js" > /dev/null; then
    echo "✅ Server is running"
else
    echo "❌ Server is not running"
fi
EOF

chmod +x monitor_duplicates.sh
./monitor_duplicates.sh
```

### Set Up Daily Monitoring (Optional)

```bash
# Add to crontab for daily duplicate monitoring
(crontab -l 2>/dev/null; echo "0 9 * * * ~/monitor_duplicates.sh >> ~/duplicate_monitoring.log") | crontab -
```

## 🔄 Rollback Plan (If Needed)

If something goes wrong:

```bash
# 1. Stop new server
sudo pkill -f "node server.js"

# 2. Restore old server
cd ~/onesec-server
sudo cp server.js.backup.* server.js  # Use the most recent backup

# 3. Remove database constraints (if needed)
sudo -u postgres psql -d onesec_analytics -c "
ALTER TABLE app_sessions DROP CONSTRAINT IF EXISTS unique_app_session;
ALTER TABLE interventions DROP CONSTRAINT IF EXISTS unique_intervention;
ALTER TABLE daily_summaries DROP CONSTRAINT IF EXISTS unique_daily_summary;
ALTER TABLE device_status DROP CONSTRAINT IF EXISTS unique_device_status;
"

# 4. Restart old server
nohup node server.js > server.log 2>&1 &
```

## 🎉 Success!

Your server now has enterprise-grade duplicate prevention:

- **Database Level**: Unique constraints prevent exact duplicates
- **Server Level**: Pre-insertion checks with graceful handling
- **Monitoring**: Real-time logging of duplicate attempts
- **Traceability**: UUID tracking for better debugging

### What You'll See Working:

1. **Normal Operation**: No user-visible changes, data flows normally
2. **Duplicate Detection**: Server logs show "Duplicate detected, skipping..." messages
3. **Data Integrity**: Research data remains clean and reliable
4. **Error Resilience**: System handles edge cases gracefully

Your OneSecClone research app now has bulletproof data integrity! 🛡️

## 📞 Need Help?

- **Check server logs**: `tail -f ~/onesec-server/server.log`
- **Monitor duplicates**: `~/monitor_duplicates.sh`
- **Test health**: `curl http://44.247.94.119:8080/api/health`
- **Database status**: `sudo -u postgres psql -d onesec_analytics -c "SELECT COUNT(*) FROM app_sessions;"`
