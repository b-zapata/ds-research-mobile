#!/bin/bash
# Bash script to update server IP across all project files
# Usage: ./scripts/update_server_ip.sh "192.168.1.100"

if [ $# -eq 0 ]; then
    echo "❌ Error: Please provide new IP address"
    echo "Usage: ./scripts/update_server_ip.sh \"192.168.1.100\""
    exit 1
fi

NEW_IP="$1"

# Validate IP format
if [[ ! $NEW_IP =~ ^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$ ]]; then
    echo "❌ Invalid IP format. Please use format: 192.168.1.100"
    exit 1
fi

echo "🔄 Updating server IP to: $NEW_IP"

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# 1. Update AppConfig.kt
echo "📝 Updating AppConfig.kt..."
APP_CONFIG_PATH="$PROJECT_ROOT/app/src/main/java/com/example/onesecclone/config/AppConfig.kt"
if [ -f "$APP_CONFIG_PATH" ]; then
    sed -i.bak "s/private const val SERVER_IP = \"[0-9.]*\"/private const val SERVER_IP = \"$NEW_IP\"/" "$APP_CONFIG_PATH"
    echo "✅ AppConfig.kt updated"
else
    echo "⚠️ AppConfig.kt not found"
fi

# 2. Update build.gradle.kts
echo "📝 Updating build.gradle.kts..."
BUILD_GRADLE_PATH="$PROJECT_ROOT/app/build.gradle.kts"
if [ -f "$BUILD_GRADLE_PATH" ]; then
    sed -i.bak "s|http://[0-9.]*:8080/|http://$NEW_IP:8080/|g" "$BUILD_GRADLE_PATH"
    echo "✅ build.gradle.kts updated"
else
    echo "⚠️ build.gradle.kts not found"
fi

# 3. Update network_security_config.xml
echo "📝 Updating network_security_config.xml..."
NETWORK_CONFIG_PATH="$PROJECT_ROOT/app/src/main/res/xml/network_security_config.xml"
if [ -f "$NETWORK_CONFIG_PATH" ]; then
    sed -i.bak "s|<domain includeSubdomains=\"false\">[0-9.]*</domain>|<domain includeSubdomains=\"false\">$NEW_IP</domain>|g" "$NETWORK_CONFIG_PATH"
    echo "✅ network_security_config.xml updated"
else
    echo "⚠️ network_security_config.xml not found"
fi

echo ""
echo "🎉 SUCCESS! Server IP updated to $NEW_IP in all files"
echo ""
echo "📋 Next steps:"
echo "  1. Clean and rebuild your project"
echo "  2. Install fresh APK on device"
echo "  3. Test connection to new server"
echo ""
