# PowerShell script to update server IP across all project files
# Usage: .\scripts\update_server_ip.ps1 "192.168.1.100"

param(
    [Parameter(Mandatory=$true)]
    [string]$newIP
)

Write-Host "🔄 Updating server IP to: $newIP" -ForegroundColor Green

# Validate IP format
if ($newIP -notmatch '^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$') {
    Write-Host "❌ Invalid IP format. Please use format: 192.168.1.100" -ForegroundColor Red
    exit 1
}

$projectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

try {
    # 1. Update AppConfig.kt
    Write-Host "📝 Updating AppConfig.kt..."
    $appConfigPath = "$projectRoot\app\src\main\java\com\example\onesecclone\config\AppConfig.kt"
    if (Test-Path $appConfigPath) {
        $content = Get-Content $appConfigPath -Raw
        $content = $content -replace 'private const val SERVER_IP = "[\d\.]+"', "private const val SERVER_IP = `"$newIP`""
        Set-Content $appConfigPath $content -NoNewline
        Write-Host "✅ AppConfig.kt updated" -ForegroundColor Green
    } else {
        Write-Host "⚠️ AppConfig.kt not found" -ForegroundColor Yellow
    }

    # 2. Update build.gradle.kts
    Write-Host "📝 Updating build.gradle.kts..."
    $buildGradlePath = "$projectRoot\app\build.gradle.kts"
    if (Test-Path $buildGradlePath) {
        $content = Get-Content $buildGradlePath -Raw
        $content = $content -replace 'http://[\d\.]+:8080/', "http://${newIP}:8080/"
        Set-Content $buildGradlePath $content -NoNewline
        Write-Host "✅ build.gradle.kts updated" -ForegroundColor Green
    } else {
        Write-Host "⚠️ build.gradle.kts not found" -ForegroundColor Yellow
    }

    # 3. Update network_security_config.xml
    Write-Host "📝 Updating network_security_config.xml..."
    $networkConfigPath = "$projectRoot\app\src\main\res\xml\network_security_config.xml"
    if (Test-Path $networkConfigPath) {
        $content = Get-Content $networkConfigPath -Raw
        $content = $content -replace '<domain includeSubdomains="false">[\d\.]+</domain>', "<domain includeSubdomains=`"false`">$newIP</domain>"
        Set-Content $networkConfigPath $content -NoNewline
        Write-Host "✅ network_security_config.xml updated" -ForegroundColor Green
    } else {
        Write-Host "⚠️ network_security_config.xml not found" -ForegroundColor Yellow
    }

    Write-Host ""
    Write-Host "🎉 SUCCESS! Server IP updated to $newIP in all files" -ForegroundColor Green
    Write-Host ""
    Write-Host "📋 Next steps:" -ForegroundColor Cyan
    Write-Host "  1. Clean and rebuild your project"
    Write-Host "  2. Install fresh APK on device"
    Write-Host "  3. Test connection to new server"
    Write-Host ""

} catch {
    Write-Host "❌ Error updating files: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
