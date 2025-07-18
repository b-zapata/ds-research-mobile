package com.example.onesecclone

import android.app.DatePickerDialog
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.onesecclone.analytics.AnalyticsData
import com.example.onesecclone.analytics.AnalyticsService
import com.example.onesecclone.network.DataSyncService
import com.example.onesecclone.utils.UsagePermissionHelper
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class StudyInfoFragment : Fragment() {

    private lateinit var btnStartDate: Button
    private lateinit var btnEndDate: Button
    private lateinit var btnSendBatch: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvAnalyticsSummary: TextView

    private var startDate: LocalDate? = null
    private var endDate: LocalDate? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_study_info, container, false)

        // Initialize views
        btnStartDate = view.findViewById(R.id.btnStartDate)
        btnEndDate = view.findViewById(R.id.btnEndDate)
        btnSendBatch = view.findViewById(R.id.btnSendBatch)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvAnalyticsSummary = view.findViewById(R.id.tvAnalyticsSummary)

        setupClickListeners()
        updateAnalyticsSummary()

        return view
    }

    private fun setupClickListeners() {
        btnStartDate.setOnClickListener {
            showDatePicker { date ->
                startDate = date
                btnStartDate.text = "Start: ${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
                updateSendButtonState()
            }
        }

        btnEndDate.setOnClickListener {
            showDatePicker { date ->
                endDate = date
                btnEndDate.text = "End: ${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
                updateSendButtonState()
            }
        }

        btnSendBatch.setOnClickListener {
            sendBatchData()
        }
    }

    private fun showDatePicker(onDateSelected: (LocalDate) -> Unit) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                onDateSelected(selectedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun updateSendButtonState() {
        val canSend = startDate != null && endDate != null
        btnSendBatch.isEnabled = canSend

        if (canSend) {
            tvStatus.text = "Ready to send data from ${startDate} to ${endDate}"
        } else {
            tvStatus.text = "Select both start and end dates to enable sending"
        }
    }

    private fun sendBatchData() {
        val start = startDate
        val end = endDate

        if (start == null || end == null) {
            Toast.makeText(requireContext(), "Please select both start and end dates", Toast.LENGTH_SHORT).show()
            return
        }

        if (start.isAfter(end)) {
            Toast.makeText(requireContext(), "Start date must be before end date", Toast.LENGTH_SHORT).show()
            return
        }

        tvStatus.text = "Collecting data from ${start} to ${end}..."
        btnSendBatch.isEnabled = false

        lifecycleScope.launch {
            try {
                val analyticsService = AnalyticsService.getInstance()
                if (analyticsService == null) {
                    tvStatus.text = "Error: Analytics service not running"
                    btnSendBatch.isEnabled = true
                    return@launch
                }

                // Get all analytics data and filter by date range
                val filteredData = getFilteredAnalyticsData(start, end)

                if (filteredData.isEmpty()) {
                    tvStatus.text = "No data found in the selected date range"
                    btnSendBatch.isEnabled = true
                    return@launch
                }

                tvStatus.text = "Sending ${filteredData.size} items to server..."

                // Send the batch data
                val dataSyncService = DataSyncService.getInstance(requireContext())
                val success = dataSyncService.sendBatchData(filteredData)

                if (success) {
                    tvStatus.text = "✅ Successfully sent ${filteredData.size} items to server!"
                    Toast.makeText(requireContext(), "Data sent successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    tvStatus.text = "❌ Failed to send data. Check your internet connection."
                    Toast.makeText(requireContext(), "Failed to send data", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                tvStatus.text = "❌ Error: ${e.message}"
                Toast.makeText(requireContext(), "Error sending data: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnSendBatch.isEnabled = true
            }
        }
    }

    private fun getFilteredAnalyticsData(startDate: LocalDate, endDate: LocalDate): List<AnalyticsData> {
        val filteredData = mutableListOf<AnalyticsData>()

        // First, get data from AnalyticsService (if any)
        val sessions = AnalyticsService.getAllSessions()
        sessions.forEach { session ->
            val sessionDate = session.sessionStart.toLocalDate()
            if (!sessionDate.isBefore(startDate) && !sessionDate.isAfter(endDate)) {
                filteredData.add(session)
            }
        }

        val taps = AnalyticsService.getAllTaps()
        taps.forEach { tap ->
            val tapDate = tap.timestamp.toLocalDate()
            if (!tapDate.isBefore(startDate) && !tapDate.isAfter(endDate)) {
                filteredData.add(tap)
            }
        }

        val interventions = AnalyticsService.getAllInterventions()
        interventions.forEach { intervention ->
            val interventionDate = intervention.interventionStart.toLocalDate()
            if (!interventionDate.isBefore(startDate) && !interventionDate.isAfter(endDate)) {
                filteredData.add(intervention)
            }
        }

        // If no data from AnalyticsService, try to get real usage stats
        if (filteredData.isEmpty()) {
            val usageStatsData = getRealUsageStatsData(startDate, endDate)
            filteredData.addAll(usageStatsData)
        }

        return filteredData
    }

    private fun getRealUsageStatsData(startDate: LocalDate, endDate: LocalDate): List<AnalyticsData> {
        val usageData = mutableListOf<AnalyticsData>()

        // Check if we have usage stats permission
        if (!UsagePermissionHelper.hasUsageStatsPermission(requireContext())) {
            Toast.makeText(requireContext(), "Usage Access permission required. Opening settings...", Toast.LENGTH_LONG).show()
            UsagePermissionHelper.openUsageAccessSettings(requireContext())
            return emptyList()
        }

        try {
            val usageStatsManager = requireContext().getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

            // Convert LocalDate to milliseconds
            val startTime = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endTime = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            // Get usage stats for the date range
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            // Convert usage stats to our analytics format
            usageStats?.forEach { usageStat ->
                if (usageStat.totalTimeInForeground > 0) {
                    val appName = getAppNameFromPackage(usageStat.packageName)

                    // Create a session for this app usage
                    val sessionStart = ZonedDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(usageStat.firstTimeStamp),
                        ZoneId.systemDefault()
                    )
                    val sessionEnd = ZonedDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(usageStat.lastTimeStamp),
                        ZoneId.systemDefault()
                    )

                    val session = AnalyticsData.AppSession(
                        appName = appName,
                        packageName = usageStat.packageName,
                        sessionStart = sessionStart,
                        sessionEnd = sessionEnd
                    )
                    usageData.add(session)

                    // Also create a tap event for each app
                    val tap = AnalyticsData.AppTap(
                        timestamp = sessionStart,
                        appName = appName,
                        packageName = usageStat.packageName
                    )
                    usageData.add(tap)
                }
            }

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error accessing usage stats: ${e.message}", Toast.LENGTH_LONG).show()
        }

        return usageData
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val packageManager = requireContext().packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            packageName // Return package name if we can't get the app name
        }
    }

    private fun updateAnalyticsSummary() {
        val analyticsService = AnalyticsService.getInstance()
        if (analyticsService == null) {
            tvAnalyticsSummary.text = "Analytics service not running"
            return
        }

        val sessionCount = AnalyticsService.getSessionCount()
        val tapCount = AnalyticsService.getTapCount()
        val interventionCount = AnalyticsService.getInterventionCount()

        val summary = """
            Sessions: $sessionCount
            Taps: $tapCount
            Interventions: $interventionCount
            
            Recent Sessions:
            ${AnalyticsService.getRecentSessions(3).joinToString("\n") { 
                "• ${it.appName} - ${it.sessionStart.format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))}"
            }}
            
            Recent Taps:
            ${AnalyticsService.getRecentTaps(3).joinToString("\n") { 
                "• ${it.appName} - ${it.timestamp.format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))}"
            }}
        """.trimIndent()

        tvAnalyticsSummary.text = summary
    }

    override fun onResume() {
        super.onResume()
        updateAnalyticsSummary()
        updateSendButtonState()
    }
}
