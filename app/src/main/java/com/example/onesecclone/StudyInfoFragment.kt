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
        val allTaps = AnalyticsService.getAllTaps()
        val allInterventions = AnalyticsService.getAllInterventions()

        // Filter sessions by date/time range
        allSessions.forEach { session ->
            val sessionTime = session.getSessionStartTime()
            if (!sessionTime.isBefore(startDateTime) && !sessionTime.isAfter(endDateTime)) {
                filteredData.add(session)
            }
        }

        // Filter taps by date/time range
        allTaps.forEach { tap ->
            val tapTime = tap.getTimestamp()
            if (!tapTime.isBefore(startDateTime) && !tapTime.isAfter(endDateTime)) {
                filteredData.add(tap)
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
            val allTaps = AnalyticsService.getAllTaps()
            val allInterventions = AnalyticsService.getAllInterventions()

            val summary = buildString {
                appendLine("📊 Data Ready for Next Batch Send:")
                appendLine()

                // App Taps Section - Show ALL taps
                appendLine("🔸 APP TAPS (${allTaps.size} total):")
                if (allTaps.isEmpty()) {
                    appendLine("   No taps recorded")
                } else {
                    val tapsByApp = allTaps.groupBy { it.appName }
                    tapsByApp.forEach { (appName, taps) ->
                        appendLine("   $appName: ${taps.size} taps")
                        taps.forEach { tap ->
                            val time = tap.getTimestamp().format(DateTimeFormatter.ofPattern("MM/dd HH:mm:ss"))
                            appendLine("     • $time")
                        }
                    }
                }
                appendLine()

                // App Sessions Section - Show ALL sessions
                appendLine("🔸 APP SESSIONS (${allSessions.size} total):")
                if (allSessions.isEmpty()) {
                    appendLine("   No sessions recorded")
                } else {
                    val sessionsByApp = allSessions.groupBy { it.appName }
                    sessionsByApp.forEach { (appName, sessions) ->
                        appendLine("   $appName: ${sessions.size} sessions")
                        sessions.forEach { session ->
                            val start = session.getSessionStartTime().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
                            val end = session.getSessionEndTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                            val duration = java.time.Duration.between(session.getSessionStartTime(), session.getSessionEndTime()).toMinutes()
                            appendLine("     • $start-$end (${duration}m)")
                        }
                    }
                }
                appendLine()

                // Interventions Section - Show ALL interventions
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
                val totalItems = allTaps.size + allSessions.size + allInterventions.size
                appendLine("📦 TOTAL ITEMS TO SEND: $totalItems")
                appendLine()
                appendLine("💡 Use date/time range above to filter")
                appendLine("   specific data for testing")
            }

            tvAnalyticsSummary.text = summary
        } catch (e: Exception) {
            tvAnalyticsSummary.text = "Error loading batch preview: ${e.message}"
        }
    }
}

