package com.example.onesecclone

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class StudyInfoFragment : Fragment() {

    private lateinit var btnStartDate: Button
    private lateinit var btnStartTime: Button
    private lateinit var btnEndDate: Button
    private lateinit var btnEndTime: Button
    private lateinit var btnSendBatch: Button
    private lateinit var btnDeleteAllData: Button
    private lateinit var btnRefreshData: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvAnalyticsSummary: TextView
    private lateinit var tvStartDateTime: TextView
    private lateinit var tvEndDateTime: TextView

    private var startDate: LocalDate? = null
    private var startTime: LocalTime? = null
    private var endDate: LocalDate? = null
    private var endTime: LocalTime? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_study_info, container, false)

        // Initialize views
        btnStartDate = view.findViewById(R.id.btnStartDate)
        btnStartTime = view.findViewById(R.id.btnStartTime)
        btnEndDate = view.findViewById(R.id.btnEndDate)
        btnEndTime = view.findViewById(R.id.btnEndTime)
        btnSendBatch = view.findViewById(R.id.btnSendBatch)
        btnDeleteAllData = view.findViewById(R.id.btnDeleteAllData)
        btnRefreshData = view.findViewById(R.id.btnRefreshData)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvAnalyticsSummary = view.findViewById(R.id.tvAnalyticsSummary)
        tvStartDateTime = view.findViewById(R.id.tvStartDateTime)
        tvEndDateTime = view.findViewById(R.id.tvEndDateTime)

        setupClickListeners()
        updateAnalyticsSummary()

        return view
    }

    private fun setupClickListeners() {
        btnStartDate.setOnClickListener {
            showDatePicker { date ->
                startDate = date
                btnStartDate.text = "Date: ${date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))}"
                updateDateTimeDisplay()
                updateSendButtonState()
            }
        }

        btnStartTime.setOnClickListener {
            showTimePicker { time ->
                startTime = time
                btnStartTime.text = "Time: ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                updateDateTimeDisplay()
                updateSendButtonState()
            }
        }

        btnEndDate.setOnClickListener {
            showDatePicker { date ->
                endDate = date
                btnEndDate.text = "Date: ${date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))}"
                updateDateTimeDisplay()
                updateSendButtonState()
            }
        }

        btnEndTime.setOnClickListener {
            showTimePicker { time ->
                endTime = time
                btnEndTime.text = "Time: ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                updateDateTimeDisplay()
                updateSendButtonState()
            }
        }

        btnSendBatch.setOnClickListener {
            sendBatchData()
        }

        btnDeleteAllData.setOnClickListener {
            deleteAllData()
        }

        btnRefreshData.setOnClickListener {
            refreshData()
        }

        // Add long press for detailed debug info
        btnRefreshData.setOnLongClickListener {
            showDetailedDebugInfo()
            true
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

    private fun showTimePicker(onTimeSelected: (LocalTime) -> Unit) {
        val calendar = Calendar.getInstance()
        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val selectedTime = LocalTime.of(hourOfDay, minute)
                onTimeSelected(selectedTime)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        timePickerDialog.show()
    }

    private fun updateDateTimeDisplay() {
        // Update start date/time display
        val startDateTime = getStartDateTime()
        if (startDateTime != null) {
            tvStartDateTime.text = "Start: ${startDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}"
        } else {
            val startParts = mutableListOf<String>()
            startDate?.let { startParts.add(it.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) }
            startTime?.let { startParts.add(it.format(DateTimeFormatter.ofPattern("HH:mm"))) }
            tvStartDateTime.text = "Start: ${if (startParts.isNotEmpty()) startParts.joinToString(" ") else "Not selected"}"
        }

        // Update end date/time display
        val endDateTime = getEndDateTime()
        if (endDateTime != null) {
            tvEndDateTime.text = "End: ${endDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}"
        } else {
            val endParts = mutableListOf<String>()
            endDate?.let { endParts.add(it.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) }
            endTime?.let { endParts.add(it.format(DateTimeFormatter.ofPattern("HH:mm"))) }
            tvEndDateTime.text = "End: ${if (endParts.isNotEmpty()) endParts.joinToString(" ") else "Not selected"}"
        }
    }

    private fun getStartDateTime(): ZonedDateTime? {
        return if (startDate != null && startTime != null) {
            ZonedDateTime.of(startDate, startTime, ZoneId.systemDefault())
        } else null
    }

    private fun getEndDateTime(): ZonedDateTime? {
        return if (endDate != null && endTime != null) {
            ZonedDateTime.of(endDate, endTime, ZoneId.systemDefault())
        } else null
    }

    private fun updateSendButtonState() {
        val canSend = startDate != null && startTime != null && endDate != null && endTime != null
        btnSendBatch.isEnabled = canSend

        if (canSend) {
            val startDateTime = getStartDateTime()
            val endDateTime = getEndDateTime()
            tvStatus.text = "Ready to send data from ${startDateTime?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))} to ${endDateTime?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}"
        } else {
            tvStatus.text = "Select both start and end dates AND times to enable sending"
        }
    }

    private fun sendBatchData() {
        val startDateTime = getStartDateTime()
        val endDateTime = getEndDateTime()

        if (startDateTime == null || endDateTime == null) {
            Toast.makeText(requireContext(), "Please select both start and end dates AND times", Toast.LENGTH_SHORT).show()
            return
        }

        if (startDateTime.isAfter(endDateTime)) {
            Toast.makeText(requireContext(), "Start date/time must be before end date/time", Toast.LENGTH_SHORT).show()
            return
        }

        tvStatus.text = "Collecting data from ${startDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))} to ${endDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}..."
        btnSendBatch.isEnabled = false

        lifecycleScope.launch {
            try {
                val analyticsService = AnalyticsService.getInstance()
                if (analyticsService == null) {
                    tvStatus.text = "Error: Analytics service not running"
                    btnSendBatch.isEnabled = true
                    return@launch
                }

                // Get all analytics data and filter by date/time range
                val filteredData = getFilteredAnalyticsData(startDateTime, endDateTime)

                if (filteredData.isEmpty()) {
                    tvStatus.text = "No data found in the selected date/time range"
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
                Toast.makeText(requireContext(), "Error occurred: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnSendBatch.isEnabled = true
            }
        }
    }

    private fun getFilteredAnalyticsData(startDateTime: ZonedDateTime, endDateTime: ZonedDateTime): List<AnalyticsData> {
        val filteredData = mutableListOf<AnalyticsData>()

        // Get all analytics data from the service
        val allSessions = AnalyticsService.getAllSessions()
        val allInterventions = AnalyticsService.getAllInterventions()

        // Filter sessions by date/time range
        allSessions.forEach { session ->
            val sessionTime = session.getSessionStartTime()
            if (!sessionTime.isBefore(startDateTime) && !sessionTime.isAfter(endDateTime)) {
                filteredData.add(session)
            }
        }

        // Filter interventions by date/time range
        allInterventions.forEach { intervention ->
            val interventionTime = intervention.getInterventionStartTime()
            if (!interventionTime.isBefore(startDateTime) && !interventionTime.isAfter(endDateTime)) {
                filteredData.add(intervention)
            }
        }

        return filteredData
    }

    private fun updateAnalyticsSummary() {
        try {
            val allSessions = AnalyticsService.getAllSessions()
            val allInterventions = AnalyticsService.getAllInterventions()

            val summary = buildString {
                appendLine("📊 Data Ready for Next Batch Send:")
                appendLine()

                // App Sessions Section
                appendLine("🔸 APP USAGE SESSIONS (${allSessions.size} total):")
                if (allSessions.isEmpty()) {
                    appendLine("   No sessions recorded")
                } else {
                    val sessionsByApp = allSessions.groupBy { it.appName }
                    sessionsByApp.forEach { (appName, sessions) ->
                        val totalTime = sessions.sumOf {
                            java.time.Duration.between(it.getSessionStartTime(), it.getSessionEndTime()).toMinutes()
                        }
                        appendLine("   $appName: ${sessions.size} sessions (${totalTime}m total)")

                        // Show ALL sessions
                        sessions.sortedByDescending { it.getSessionStartTime() }
                            .forEach { session ->
                                val start = session.getSessionStartTime().format(DateTimeFormatter.ofPattern("MM/dd HH:mm:ss"))
                                val end = session.getSessionEndTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                                val duration = java.time.Duration.between(session.getSessionStartTime(), session.getSessionEndTime())
                                val hours = duration.toHours()
                                val minutes = duration.toMinutes() % 60
                                val seconds = duration.seconds % 60

                                val durationStr = when {
                                    hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
                                    minutes > 0 -> "${minutes}m ${seconds}s"
                                    else -> "${seconds}s"
                                }

                                appendLine("     • $start-$end ($durationStr)")
                            }
                        appendLine()
                    }
                }

                // Interventions Section
                appendLine("🔸 INTERVENTIONS (${allInterventions.size} total):")
                if (allInterventions.isEmpty()) {
                    appendLine("   No interventions recorded")
                } else {
                    allInterventions.forEach { intervention ->
                        val start = intervention.getInterventionStartTime().format(DateTimeFormatter.ofPattern("MM/dd HH:mm:ss"))
                        val duration = java.time.Duration.between(intervention.getInterventionStartTime(), intervention.getInterventionEndTime()).toSeconds()
                        appendLine("   ${intervention.appName}: ${intervention.buttonClicked}")
                        appendLine("     • $start (${duration}s duration)")
                    }
                }
                appendLine()

                // Summary
                val totalItems = allSessions.size + allInterventions.size
                appendLine("📦 TOTAL ITEMS TO SEND: $totalItems")
            }

            tvAnalyticsSummary.text = summary
        } catch (e: Exception) {
            tvAnalyticsSummary.text = "Error loading batch preview: ${e.message}"
        }
    }

    private fun deleteAllData() {
        // Show confirmation dialog since this is a destructive action
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete All Data")
            .setMessage(
                "⚠️ This will permanently delete ALL collected data:\n\n" +
                "• App usage sessions\n" +
                "• Intervention records\n" +
                "• Any remaining tap data\n\n" +
                "This action cannot be undone. Are you sure?"
            )
            .setPositiveButton("Delete All") { _, _ ->
                performDeleteAllData()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    private fun performDeleteAllData() {
        try {
            val analyticsService = AnalyticsService.getInstance()
            if (analyticsService == null) {
                tvStatus.text = "Error: Analytics service not running"
                Toast.makeText(requireContext(), "Analytics service not available", Toast.LENGTH_SHORT).show()
                return
            }

            // Disable delete button during operation
            btnDeleteAllData.isEnabled = false
            tvStatus.text = "Deleting all data..."

            // Clear all data
            val deletedCount = AnalyticsService.clearAllDataStatic()

            // Update UI to reflect changes
            updateAnalyticsSummary()

            // Show success message
            tvStatus.text = "✅ Successfully deleted $deletedCount data items"
            Toast.makeText(requireContext(), "All data deleted successfully!", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            tvStatus.text = "❌ Error deleting data: ${e.message}"
            Toast.makeText(requireContext(), "Failed to delete data: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            btnDeleteAllData.isEnabled = true
        }
    }

    private fun refreshData() {
        // Add diagnostic information
        val analyticsService = AnalyticsService.getInstance()
        val serviceStatus = if (analyticsService != null) "✅ Running" else "❌ Not Running"

        updateAnalyticsSummary()
        Toast.makeText(requireContext(), "Data refreshed! Analytics Service: $serviceStatus", Toast.LENGTH_LONG).show()
    }

    private fun showDetailedDebugInfo() {
        try {
            val allSessions = AnalyticsService.getAllSessions()
            val allInterventions = AnalyticsService.getAllInterventions()
            val allTaps = AnalyticsService.getAllTaps()

            val debugInfo = buildString {
                appendLine("🔍 DETAILED DEBUG INFO")
                appendLine("=".repeat(30))
                appendLine()

                appendLine("📊 RAW DATA COUNTS:")
                appendLine("• Sessions: ${allSessions.size}")
                appendLine("• Interventions: ${allInterventions.size}")
                appendLine("• Taps: ${allTaps.size}")
                appendLine()

                appendLine("🕒 RAW SESSION DATA:")
                if (allSessions.isEmpty()) {
                    appendLine("   No sessions found")
                } else {
                    allSessions.forEachIndexed { index, session ->
                        appendLine("   Session ${index + 1}:")
                        appendLine("     App: ${session.appName}")
                        appendLine("     Package: ${session.packageName}")
                        appendLine("     Start: ${session.sessionStart}")
                        appendLine("     End: ${session.sessionEnd}")
                        appendLine("     EventType: ${session.eventType}")
                        appendLine("     Duration: ${java.time.Duration.between(session.getSessionStartTime(), session.getSessionEndTime()).toMinutes()}m")
                        appendLine()
                    }
                }

                appendLine("🎯 RAW TAP DATA:")
                if (allTaps.isEmpty()) {
                    appendLine("   No taps found")
                } else {
                    allTaps.forEachIndexed { index, tap ->
                        appendLine("   Tap ${index + 1}:")
                        appendLine("     App: ${tap.appName}")
                        appendLine("     Package: ${tap.packageName}")
                        appendLine("     Timestamp: ${tap.timestamp}")
                        appendLine("     EventType: ${tap.eventType}")
                        appendLine()
                    }
                }

                appendLine("🛠️ RAW INTERVENTION DATA:")
                if (allInterventions.isEmpty()) {
                    appendLine("   No interventions found")
                } else {
                    allInterventions.forEachIndexed { index, intervention ->
                        appendLine("   Intervention ${index + 1}:")
                        appendLine("     App: ${intervention.appName}")
                        appendLine("     Type: ${intervention.interventionType}")
                        appendLine("     Button: ${intervention.buttonClicked}")
                        appendLine("     Start: ${intervention.interventionStart}")
                        appendLine("     End: ${intervention.interventionEnd}")
                        appendLine("     EventType: ${intervention.eventType}")
                        appendLine()
                    }
                }
            }

            // Show in a dialog
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Raw Database Contents")
                .setMessage(debugInfo.toString())
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error getting debug info: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
