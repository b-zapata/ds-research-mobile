package com.example.onesecclone

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.example.onesecclone.utils.UsagePermissionHelper
import com.example.onesecclone.analytics.AnalyticsService
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ScreenTimeFragment : Fragment() {

    private val viewModel: ScreenTimeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_screen_time, container, false)

        val containerLayout = rootView.findViewById<LinearLayout>(R.id.usage_list)
        val grantButton = rootView.findViewById<Button>(R.id.grant_permission_button)
        val tabLayout = rootView.findViewById<TabLayout>(R.id.tab_layout)

        if (!UsagePermissionHelper.hasUsageStatsPermission(requireContext())) {
            grantButton.visibility = View.VISIBLE
            containerLayout.visibility = View.GONE
            tabLayout.visibility = View.GONE
            grantButton.setOnClickListener {
                UsagePermissionHelper.openUsageAccessSettings(requireContext())
            }
        } else {
            grantButton.visibility = View.GONE
            containerLayout.visibility = View.VISIBLE
            tabLayout.visibility = View.VISIBLE

            // Default time range: past 1 day
            val end = System.currentTimeMillis()
            val start = end - TimeUnit.DAYS.toMillis(1)

            // Load and display daily totals initially
            viewModel.loadUsageStats(start, end)

            viewModel.usageData.observe(viewLifecycleOwner, Observer { usageList ->
                if (tabLayout.selectedTabPosition == 0) { // Only update if Daily Totals tab is selected
                    containerLayout.removeAllViews()
                    usageList.forEach { app ->
                        val textView = TextView(requireContext())
                        textView.text = "${app.packageName}: ${app.usageMinutes} minutes"
                        containerLayout.addView(textView)
                    }
                }
            })

            // Handle tab selection changes
            tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    containerLayout.removeAllViews()

                    when (tab?.position) {
                        0 -> { // Daily Totals
                            viewModel.loadUsageStats(start, end)
                        }
                        1 -> { // Sessions
                            displayAppSessions(containerLayout, start, end)
                        }
                        2 -> { // Analytics Data
                            displayAnalyticsData(containerLayout)
                        }
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
        }

        return rootView
    }

    private fun displayAppSessions(containerLayout: LinearLayout, start: Long, end: Long) {
        val usageStatsManager = requireContext().getSystemService(UsageStatsManager::class.java)
        val events = usageStatsManager.queryEvents(start, end)
        val event = UsageEvents.Event()

        val dateFormat = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
        var currentApp: String? = null
        var sessionStart: Long = 0
        val sessions = mutableListOf<SessionData>()

        val appNames = mapOf(
            "com.instagram.android" to "Instagram",
            "com.facebook.katana" to "Facebook",
            "com.google.android.youtube" to "YouTube"
        )

        // Collect all sessions first
        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && appNames.containsKey(event.packageName)) {
                currentApp = event.packageName
                sessionStart = event.timeStamp
            } else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND && event.packageName == currentApp) {
                val sessionEnd = event.timeStamp
                val durationMs = sessionEnd - sessionStart
                val minutes = durationMs / 60000
                val seconds = (durationMs % 60000) / 1000

                val appLabel = appNames[event.packageName] ?: event.packageName
                val formatted = "$appLabel: ${dateFormat.format(Date(sessionStart))} - ${dateFormat.format(Date(sessionEnd))} (${minutes}m ${seconds}s)"

                sessions.add(SessionData(sessionStart, formatted))
            }
        }

        // Sort sessions by start time in descending order (newest first)
        sessions.sortByDescending { it.startTime }

        // Display sorted sessions
        sessions.forEach { session ->
            val textView = TextView(requireContext())
            textView.text = session.formattedText
            containerLayout.addView(textView)
        }
    }

    // Data class to hold session information for sorting
    private data class SessionData(
        val startTime: Long,
        val formattedText: String
    )

    private fun displayAnalyticsData(containerLayout: LinearLayout) {
        val analyticsService = AnalyticsService.getInstance()

        if (analyticsService == null) {
            // Analytics service not running
            addSectionHeader(containerLayout, "📊 Analytics Service Status")
            addDataRow(containerLayout, "❌ Analytics Service", "Not Running")
            addDataRow(containerLayout, "💡 Tip", "Enable AnalyticsService in MainDashboardActivity")
            return
        }

        // Service Status Section
        addSectionHeader(containerLayout, "📊 Analytics Service Status")
        addDataRow(containerLayout, "✅ Service Status", "Running")
        addDataRow(containerLayout, "📈 Total Counts", AnalyticsService.getAnalyticsCounts())

        // Get current analytics data using the new public methods
        val sessionCount = AnalyticsService.getSessionCount()
        val tapCount = AnalyticsService.getTapCount()
        val interventionCount = AnalyticsService.getInterventionCount()

        // Data Collection Summary
        addSectionHeader(containerLayout, "📱 Data Collection Summary")
        addDataRow(containerLayout, "App Sessions", "$sessionCount recorded")
        addDataRow(containerLayout, "App Taps", "$tapCount recorded")
        addDataRow(containerLayout, "Interventions", "$interventionCount recorded")

        // Recent App Sessions
        val recentSessions = AnalyticsService.getRecentSessions(5)
        if (recentSessions.isNotEmpty()) {
            addSectionHeader(containerLayout, "📱 Recent App Sessions")
            recentSessions.forEach { session ->
                val totalSeconds = java.time.temporal.ChronoUnit.SECONDS.between(
                    session.getSessionStartTime(), session.getSessionEndTime())
                val durationText = when {
                    totalSeconds < 60 -> "${totalSeconds}s"
                    totalSeconds < 3600 -> {
                        val minutes = totalSeconds / 60
                        val seconds = totalSeconds % 60
                        if (seconds == 0L) "${minutes}m" else "${minutes}m ${seconds}s"
                    }
                    else -> {
                        val hours = totalSeconds / 3600
                        val minutes = (totalSeconds % 3600) / 60
                        if (minutes == 0L) "${hours}h" else "${hours}h ${minutes}m"
                    }
                }
                addDataRow(containerLayout, session.appName, durationText)
            }
        }

        // Recent App Taps
        val recentTaps = AnalyticsService.getRecentTaps(5)
        if (recentTaps.isNotEmpty()) {
            addSectionHeader(containerLayout, "👆 Recent App Taps")
            recentTaps.forEach { tap ->
                val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                val time = timeFormat.format(java.util.Date.from(tap.getTimestamp().toInstant()))
                addDataRow(containerLayout, tap.appName, "at $time")
            }
        }

        // Recent Interventions
        val recentInterventions = AnalyticsService.getRecentInterventions(5)
        if (recentInterventions.isNotEmpty()) {
            addSectionHeader(containerLayout, "🎬 Recent Interventions")
            recentInterventions.forEach { intervention ->
                val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                val time = timeFormat.format(java.util.Date.from(intervention.getInterventionStartTime().toInstant()))
                addDataRow(containerLayout, "${intervention.appName} - ${intervention.buttonClicked}", "at $time")
            }
        }

        // Instructions for testing
        addSectionHeader(containerLayout, "🧪 Testing Instructions")
        addDataRow(containerLayout, "1. Open Instagram", "Should record an app tap")
        addDataRow(containerLayout, "2. Wait for video overlay", "Should record intervention")
        addDataRow(containerLayout, "3. Click Close/Skip", "Should record user choice")
        addDataRow(containerLayout, "4. Switch between apps", "Should record app sessions")
        addDataRow(containerLayout, "5. Refresh this view", "To see updated data")

        // Refresh button
        val refreshButton = Button(requireContext())
        refreshButton.text = "🔄 Refresh Analytics Data"
        refreshButton.setOnClickListener {
            displayAnalyticsData(containerLayout)
        }
        containerLayout.addView(refreshButton)
    }

    private fun addSectionHeader(containerLayout: LinearLayout, title: String) {
        val headerView = TextView(requireContext())
        headerView.text = title
        headerView.textSize = 16f
        headerView.setTypeface(null, android.graphics.Typeface.BOLD)
        headerView.setPadding(0, 32, 0, 16)
        headerView.setTextColor(android.graphics.Color.parseColor("#2196F3"))
        containerLayout.addView(headerView)
    }

    private fun addDataRow(containerLayout: LinearLayout, label: String, value: String) {
        val rowView = TextView(requireContext())
        rowView.text = "$label: $value"
        rowView.textSize = 14f
        rowView.setPadding(16, 8, 0, 8)
        containerLayout.addView(rowView)
    }
}
