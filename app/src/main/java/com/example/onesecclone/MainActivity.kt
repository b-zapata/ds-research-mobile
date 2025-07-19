package com.example.onesecclone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.onesecclone.debug.DataFlowTestActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if this is a debug build and add testing option
        if (BuildConfig.DEBUG) {
            // Add a small delay to show debug options
            android.os.Handler(mainLooper).postDelayed({
                showDebugOptions()
            }, 2000)
        }

        // Ask for usage access
        if (!hasUsageStatsPermission()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            Toast.makeText(this, "Grant Usage Access for this app", Toast.LENGTH_LONG).show()
        }

        // Ask for overlay permission
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Grant Display Over Other Apps permission", Toast.LENGTH_LONG).show()
        }

        // Start the background service
        startService(Intent(this, MainService::class.java))

        // Don't finish immediately in debug mode to allow testing
        if (!BuildConfig.DEBUG) {
            finish()
        }
    }

    private fun showDebugOptions() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Debug Options")
        builder.setMessage("This is a debug build. Would you like to test the data flow to your EC2 server?")
        builder.setPositiveButton("Test Data Flow") { _, _ ->
            startActivity(Intent(this, DataFlowTestActivity::class.java))
        }
        builder.setNegativeButton("Continue Normally") { _, _ ->
            finish()
        }
        builder.setCancelable(false)
        builder.show()
    }

    private fun hasUsageStatsPermission(): Boolean {
        try {
            val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOps.checkOpNoThrow(
                "android:get_usage_stats",
                android.os.Process.myUid(), packageName
            )
            return mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            return false
        }
    }
}
