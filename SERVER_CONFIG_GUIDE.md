# Server Configuration Guide

This guide explains how to configure your OneSecClone app to connect to different servers without hard-coding IP addresses.

## Quick Setup

### Option 1: Environment Variables (Recommended for Development)

Set these environment variables on your development machine:

```bash
# For development (debug builds)
export DEV_SERVER_URL="http://YOUR_EC2_IP:8080/"

# For staging builds  
export STAGING_SERVER_URL="https://staging.your-domain.com/"

# For production builds
export PROD_SERVER_URL="https://your-domain.com/"

# Optional: API key if your server requires it
export API_KEY="your-api-key-here"
```

**Windows (Command Prompt):**
```cmd
set DEV_SERVER_URL=http://YOUR_EC2_IP:8080/
set STAGING_SERVER_URL=https://staging.your-domain.com/
set PROD_SERVER_URL=https://your-domain.com/
```

**Windows (PowerShell):**
```powershell
$env:DEV_SERVER_URL="http://YOUR_EC2_IP:8080/"
$env:STAGING_SERVER_URL="https://staging.your-domain.com/"
$env:PROD_SERVER_URL="https://your-domain.com/"
```

### Option 2: In-App Configuration

The app includes a settings screen where users can:
1. Choose from predefined environments (Production, Staging, Local)
2. Enter a custom server URL
3. View the current server configuration

### Option 3: Direct Code Configuration

Update the default URLs in `AppConfig.kt`:

```kotlin
const val DEFAULT_PRODUCTION_URL = "https://your-actual-domain.com/"
const val DEFAULT_STAGING_URL = "https://staging.your-domain.com/"
const val DEFAULT_LOCAL_URL = "http://YOUR_EC2_IP:8080/"
```

## Build Types

The app now supports three build types:

1. **Debug** - Uses `DEV_SERVER_URL` or defaults to localhost:8080
2. **Staging** - Uses `STAGING_SERVER_URL` or defaults to staging domain
3. **Release** - Uses `PROD_SERVER_URL` or defaults to production domain

## Current Configuration Priority

The app determines the server URL in this order:
1. Custom URL set by user in the app
2. Environment variable for the current build type
3. Default URL defined in AppConfig.kt

## Changing Server at Runtime

Users can change the server URL without rebuilding the app:
1. Open the app settings
2. Navigate to "Server Settings"
3. Choose a predefined environment or enter a custom URL
4. The change takes effect immediately for new requests

## For CI/CD and Deployment

Set environment variables in your build system:

**GitHub Actions:**
```yaml
env:
  DEV_SERVER_URL: ${{ secrets.DEV_SERVER_URL }}
  STAGING_SERVER_URL: ${{ secrets.STAGING_SERVER_URL }}
  PROD_SERVER_URL: ${{ secrets.PROD_SERVER_URL }}
```

**Docker:**
```dockerfile
ENV DEV_SERVER_URL=http://your-ec2-ip:8080/
ENV PROD_SERVER_URL=https://your-domain.com/
```

## Testing Different Servers

To test against different servers during development:

1. **Quick switch via environment:**
   ```bash
   export DEV_SERVER_URL="http://new-ec2-ip:8080/"
   ./gradlew assembleDebug
   ```

2. **Switch via app settings:**
   - Open app → Settings → Server Settings
   - Select "Custom" and enter new URL
   - Test immediately without rebuilding

3. **Build-specific URLs:**
   ```bash
   ./gradlew assembleDebug     # Uses DEV_SERVER_URL
   ./gradlew assembleStaging   # Uses STAGING_SERVER_URL  
   ./gradlew assembleRelease   # Uses PROD_SERVER_URL
   ```

## Troubleshooting

- **"Connection failed"**: Check if the server URL is correct and accessible
- **"Invalid URL"**: Ensure the URL includes protocol (http:// or https://)
- **Build errors**: Make sure environment variables are set correctly
- **App not connecting**: Check the current URL in app settings

## Security Notes

- Never commit actual server URLs or API keys to version control
- Use environment variables or build secrets for sensitive configuration
- Consider using domain names instead of IP addresses for production
