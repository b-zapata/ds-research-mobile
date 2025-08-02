# OneSecClone Utility Scripts

This directory contains utility scripts for maintaining and deploying the OneSecClone project.

## 📁 Available Scripts

### Server IP Management

#### `update_server_ip.ps1` (Windows PowerShell)

**Purpose**: Automatically updates server IP address across all project files  
**Usage**:

```powershell
.\scripts\update_server_ip.ps1 "44.247.94.119"
```

**What it updates**:

- `app/src/main/java/.../AppConfig.kt` - Updates SERVER_IP constant
- `app/build.gradle.kts` - Updates BuildConfig SERVER_URL
- `app/src/main/res/xml/network_security_config.xml` - Updates domain whitelist

#### `update_server_ip.sh` (Linux/macOS Bash)

**Purpose**: Same as PowerShell version but for Unix-like systems  
**Usage**:

```bash
./scripts/update_server_ip.sh "44.247.94.119"
```

**Features**:

- ✅ IP format validation
- ✅ Automatic file updates
- ✅ Error handling and rollback
- ✅ Confirmation prompts
- ✅ Success verification

## 🚀 Usage Examples

### Updating to New EC2 Instance

```powershell
# When you get a new EC2 IP address
.\scripts\update_server_ip.ps1 "54.123.456.789"
```

### Development Server Switch

```bash
# Switch to local development server
./scripts/update_server_ip.sh "192.168.1.100"
```

### After EC2 Restart

```powershell
# EC2 public IPs change on restart
.\scripts\update_server_ip.ps1 "44.247.94.119"
```

## 🔍 Script Details

### Validation Features

- **IP Format Check**: Ensures valid IPv4 format (xxx.xxx.xxx.xxx)
- **File Existence**: Verifies all target files exist before modification
- **Backup Creation**: Creates timestamped backups before changes
- **Verification**: Confirms changes were applied correctly

### Error Handling

- **Rollback**: Automatically restores backups if errors occur
- **Detailed Logging**: Shows exactly what files were modified
- **Exit Codes**: Proper exit codes for automation/CI use

### Files Modified

1. **AppConfig.kt**: Updates central SERVER_IP constant
2. **build.gradle.kts**: Updates BuildConfig SERVER_URL field
3. **network_security_config.xml**: Updates HTTP domain permissions

## 🛠️ Maintenance

### Adding New Files to Update

To add new files that need IP updates:

1. **Edit the script** (`update_server_ip.ps1` or `update_server_ip.sh`)
2. **Add new file path** to the update section
3. **Add appropriate regex** for finding/replacing IP
4. **Test thoroughly** before committing

### Testing Scripts

```powershell
# Test with a fake IP first
.\scripts\update_server_ip.ps1 "1.2.3.4"

# Verify changes in git
git diff

# Rollback if needed
git checkout -- app/src/main/java/com/example/onesecclone/config/AppConfig.kt
```

## 📚 Related Documentation

- **Manual IP Updates**: See `SERVER_CONFIG_GUIDE.md`
- **Server Deployment**: See `server/DUPLICATE_PREVENTION_DEPLOYMENT.md`
- **Configuration Management**: See `DOCUMENTATION_INDEX.md`

## 🔄 Future Enhancements

Potential script improvements:

- **Environment-specific updates** (dev/staging/prod)
- **Domain name support** (not just IP addresses)
- **Configuration validation** after updates
- **Integration with CI/CD** pipelines

---

**Note**: These scripts provide the automation behind the manual steps described in `SERVER_CONFIG_GUIDE.md`. Use the scripts for quick updates, or follow the manual guide for understanding the process.
