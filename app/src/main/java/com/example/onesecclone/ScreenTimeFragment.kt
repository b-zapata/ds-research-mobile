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
        val spinner = rootView.findViewById<Spinner>(R.id.view_mode_spinner)

        if (!UsagePermissionHelper.hasUsageStatsPermission(requireContext())) {
            grantButton.visibility = View.VISIBLE
            containerLayout.visibility = View.GONE
            spinner.visibility = View.GONE
            grantButton.setOnClickListener {
                UsagePermissionHelper.openUsageAccessSettings(requireContext())
            }
        } else {
            grantButton.visibility = View.GONE
            containerLayout.visibility = View.VISIBLE
            spinner.visibility = View.VISIBLE

            // Default time range: past 1 day
            val end = System.currentTimeMillis()
            val start = end - TimeUnit.DAYS.toMillis(1)

            // Load and display daily totals initially
            viewModel.loadUsageStats(start, end)

            viewModel.usageData.observe(viewLifecycleOwner, Observer { usageList ->
                containerLayout.removeAllViews()
                usageList.forEach { app ->
                    val textView = TextView(requireContext())
                    textView.text = "${app.packageName}: ${app.usageMinutes} minutes"
                    containerLayout.addView(textView)
                }
            })

            // Handle dropdown change
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                    val selection = parent.getItemAtPosition(pos).toString()
                    containerLayout.removeAllViews()

                    if (selection == "Daily Totals") {
                        viewModel.loadUsageStats(start, end)
                    } else if (selection == "Sessions") {
                        displayAppSessions(containerLayout, start, end)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
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

        val appNames = mapOf(
            "com.instagram.android" to "Instagram",
            "com.facebook.katana" to "Facebook",
            "com.google.android.youtube" to "YouTube"
        )

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

                val textView = TextView(requireContext())
                textView.text = formatted
                containerLayout.addView(textView)
            }
        }
    }


}
