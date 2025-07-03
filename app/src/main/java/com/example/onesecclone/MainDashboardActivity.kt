package com.example.onesecclone

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.onesecclone.analytics.AnalyticsService
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainDashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_dashboard)

        // Start the main monitoring service that detects Instagram usage
        startService(Intent(this, MainService::class.java))

        // Start analytics service - TEMPORARILY DISABLED FOR TESTING
        // startService(Intent(this, AnalyticsService::class.java))

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        navView.setupWithNavController(navController)
    }
}
