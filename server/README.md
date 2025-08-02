# OneSecClone Analytics Server

A Node.js/Express server for collecting and storing mobile app usage analytics from the OneSecClone research app.

## Features

- **RESTful API** for receiving analytics data from mobile apps
- **PostgreSQL Database** with optimized schema for research data
- **Duplicate Prevention** (Added Aug 2, 2025) - Three-layer protection system
- **Health Monitoring** and status endpoints
- **Rate Limiting** and security middleware
- **Batch Data Processing** for efficient data collection

## Current Status

**Server**: Running on `44.247.94.119:8080`  
**Database**: PostgreSQL with 4-table schema  
**Duplicate Prevention**: ✅ Active  
**Last Updated**: August 2, 2025

## API Endpoints

### Health Check

```http
GET /api/health
```

Returns server status and timestamp.

### Analytics Data

```http
POST /api/analytics/batch
Content-Type: application/json

{
  "deviceId": "unique-device-id",
  "userId": "optional-user-id",
  "data": [
    {
      "eventType": "app_session",
      "sessionId": "uuid-v4",
      "appName": "TikTok",
      "packageName": "com.ss.android.ugc.tiktok",
      "sessionStart": "2025-08-02T10:00:00Z",
      "sessionEnd": "2025-08-02T10:05:00Z"
    }
  ],
  "timestamp": "2025-08-02T10:05:00Z"
}
```

### Single Data Point

```http
POST /api/analytics/data
X-Device-ID: unique-device-id
Content-Type: application/json

{
  "eventType": "intervention",
  "interventionId": "uuid-v4",
  "interventionStart": "2025-08-02T10:00:00Z",
  "interventionEnd": "2025-08-02T10:00:30Z",
  "appName": "TikTok",
  "interventionType": "video",
  "videoDuration": 30,
  "requiredWatchTime": 15,
  "buttonClicked": "continue"
}
```

## Data Types

### App Sessions

- **Purpose**: Track individual app usage sessions
- **Duplicate Prevention**: Unique constraint on `(device_id, package_name, session_start, session_end)`
- **UUID Field**: `sessionId`

### Interventions

- **Purpose**: Track video interruptions when users open apps
- **Duplicate Prevention**: Unique constraint on `(device_id, app_name, intervention_start, intervention_end, intervention_type)`
- **UUID Field**: `interventionId`

### Device Status

- **Purpose**: Monitor device health and connectivity
- **Duplicate Prevention**: Prevents identical readings within 1-minute window
- **UUID Field**: `statusId`

### Daily Summaries

- **Purpose**: Aggregate daily usage statistics
- **Duplicate Prevention**: One summary per device per day (updates existing)
- **UUID Field**: `summaryId`

## Duplicate Prevention System

### Three-Layer Protection (Added Aug 2, 2025)

1. **Database Constraints**

   - PostgreSQL unique constraints prevent exact duplicates
   - Composite keys based on meaningful data combinations
   - Automatic constraint violation handling

2. **Server-Side Detection**

   - Pre-insertion duplicate checks
   - Graceful handling with detailed logging
   - Special logic for daily summaries (updates vs duplicates)

3. **Client-Generated UUIDs**
   - Every data point has unique identifier
   - Better traceability and debugging
   - Enhanced data integrity verification

### Monitoring Duplicates

```bash
# Check server logs for duplicate detection
tail -f server.log | grep -i "duplicate"

# Monitor database constraints
sudo -u postgres psql -d onesec_analytics -c "
SELECT conname FROM pg_constraint WHERE contype = 'u';
"
```

## Database Schema

See `DATABASE_SCHEMA.md` for complete schema documentation.

**Tables**:

- `app_sessions` - Individual app usage sessions
- `interventions` - Video interruption events
- `device_status` - Device health monitoring
- `daily_summaries` - Daily usage aggregates

**Migrations**: See `migrations/` folder for schema updates.

## Deployment

### Local Development

```bash
npm install
node server.js
```

### EC2 Production

See `DUPLICATE_PREVENTION_DEPLOYMENT.md` for latest deployment guide.

### Environment Variables

```bash
DB_USER=onesec_user
DB_HOST=localhost
DB_NAME=onesec_analytics
DB_PASSWORD=your_password
DB_PORT=5432
PORT=8080
```

## Security

- **Rate Limiting**: 100 requests per 15 minutes per IP
- **CORS**: Enabled for cross-origin requests
- **Helmet**: Security headers middleware
- **Input Validation**: JSON schema validation
- **Error Handling**: Graceful error responses

## Monitoring

### Health Check

```bash
curl http://44.247.94.119:8080/api/health
```

### Data Verification

```bash
# Check recent data
sudo -u postgres psql -d onesec_analytics -c "
SELECT COUNT(*) as total_sessions,
       COUNT(DISTINCT device_id) as unique_devices,
       MAX(created_at) as latest_data
FROM app_sessions;
"
```

### Performance

```bash
# Monitor active connections
sudo -u postgres psql -d onesec_analytics -c "
SELECT count(*) as active_connections
FROM pg_stat_activity
WHERE state = 'active';
"
```

## Files

- `server.js` - Main server application with duplicate prevention
- `database_schema.sql` - Complete database schema with constraints
- `DATABASE_SCHEMA.md` - Schema documentation
- `migrations/` - Database migration scripts
- `verify_data_flow.js` - Data verification utility
- `deploy.sh` - Deployment automation script
- `DUPLICATE_PREVENTION_DEPLOYMENT.md` - EC2 deployment guide

## Research Data Integrity

This server is designed for research applications where data integrity is critical:

- **No Data Loss**: Comprehensive error handling and logging
- **Duplicate Prevention**: Multiple layers prevent research data corruption
- **Audit Trail**: UUID tracking and detailed timestamps
- **Data Validation**: Schema validation and constraint enforcement
- **Monitoring**: Real-time duplicate detection and health monitoring

## Support

For deployment issues or questions:

1. Check `server.log` for error messages
2. Verify database connectivity
3. Test API endpoints with health check
4. Monitor duplicate prevention logs
5. Review `DUPLICATE_PREVENTION_DEPLOYMENT.md` for troubleshooting
