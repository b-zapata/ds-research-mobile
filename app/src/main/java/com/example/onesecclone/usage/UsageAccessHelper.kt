package com.example.onesecclone.usage

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

object UsageAccessHelper {

    /**
     * Check if the app has usage access permission
     */
    fun hasUsageAccessPermission(context: Context): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Show dialog explaining usage access permission and navigate to settings
     */
    fun showUsageAccessDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Usage Access Permission")
            .setMessage("To track app usage sessions, this app needs usage access permission. Would you like to grant this permission?")
            .setPositiveButton("Grant Permission") { _, _ ->
                openUsageAccessSettings(context)
            }
            .setNegativeButton("Later") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Open the usage access settings page
     */
    private fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        context.startActivity(intent)
    }
}
