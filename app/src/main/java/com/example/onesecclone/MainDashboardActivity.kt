package com.example.onesecclone

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.onesecclone.analytics.AnalyticsService
import com.example.onesecclone.usage.UsageAccessHelper
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainDashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_dashboard)

        // Check for usage access permission before starting services
        checkUsageAccessPermission()

        // Start the main monitoring service that detects app usage sessions
        startService(Intent(this, MainService::class.java))

        // Start analytics service
        startService(Intent(this, AnalyticsService::class.java))

        // Set up navigation
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.nav_view)
        bottomNav.setupWithNavController(navController)
    }

    private fun checkUsageAccessPermission() {
        if (!UsageAccessHelper.hasUsageAccessPermission(this)) {
            // Show dialog explaining the new session tracking and asking for permission
            UsageAccessHelper.showUsageAccessDialog(this)
        }
    }

    override fun onResume() {
        super.onResume()
        // Check again when user returns from settings
        if (!UsageAccessHelper.hasUsageAccessPermission(this)) {
            // Still no permission - could show a gentle reminder or continue without it
            // The app will still work but session tracking may be less accurate
        }
    }
}
