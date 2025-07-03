package com.example.onesecclone

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.onesecclone.utils.UsagePermissionHelper

class HomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val getReadyButton = view.findViewById<Button>(R.id.btn_get_ready)
        val messageText = view.findViewById<TextView>(R.id.tv_message)
        val logContainer = view.findViewById<LinearLayout>(R.id.log_container)

        // Check permissions first
        checkAndRequestPermissions()

        getReadyButton.setOnClickListener {
            // Check permissions before proceeding
            if (!hasAllPermissions()) {
                Toast.makeText(requireContext(), "Please grant all permissions first", Toast.LENGTH_LONG).show()
                checkAndRequestPermissions()
                return@setOnClickListener
            }

            // Step 1: Change button state
            getReadyButton.text = "Getting ready..."
            getReadyButton.isEnabled = false
            getReadyButton.backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.GRAY)

            // Step 2: Show log messages over time
            val logs = listOf("Log message...", "Another log message...", "Initializing session...")
            logs.forEachIndexed { index, log ->
                Handler(Looper.getMainLooper()).postDelayed({
                    val logView = TextView(requireContext())
                    logView.text = log
                    logView.setPadding(0, 8, 0, 0)
                    logContainer.addView(logView)
                }, (index + 1) * 1000L)
            }

            // Step 3: After 3 seconds, show "READY" button
            Handler(Looper.getMainLooper()).postDelayed({
                getReadyButton.visibility = View.GONE
                logContainer.removeAllViews()

                // ✅ Display the READY message with green-highlighted "READY"
                val readyText = "Status: READY. Waiting for green light from the server..."
                val readySpannable = android.text.SpannableString(readyText)
                val readyStart = readyText.indexOf("READY")
                val readyEnd = readyStart + "READY".length

                readySpannable.setSpan(
                    android.text.style.ForegroundColorSpan(Color.parseColor("#4CAF50")),
                    readyStart,
                    readyEnd,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                messageText.setText(readySpannable, TextView.BufferType.SPANNABLE)

                // ✅ Set up tap-anywhere listener to switch to HEALTHY
                val rootView = view.findViewById<ViewGroup>(R.id.layout_container)
                rootView.setOnClickListener {
                    val healthyText = "Status: Healthy. Connection is functional. App is ready to be used."
                    val healthySpannable = android.text.SpannableString(healthyText)
                    val healthyStart = healthyText.indexOf("Healthy")
                    val healthyEnd = healthyStart + "Healthy".length

                    healthySpannable.setSpan(
                        android.text.style.ForegroundColorSpan(Color.parseColor("#4CAF50")),
                        healthyStart,
                        healthyEnd,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    messageText.setText(healthySpannable, TextView.BufferType.SPANNABLE)
                    rootView.setOnClickListener(null) // Remove listener so it doesn't fire again
                }
            }, 3000L)
        }
    }

    private fun checkAndRequestPermissions() {
        val hasUsageStats = UsagePermissionHelper.hasUsageStatsPermission(requireContext())
        val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(requireContext())
        } else {
            true
        }

        if (!hasUsageStats) {
            Toast.makeText(requireContext(), "Please grant Usage Access permission to monitor Instagram", Toast.LENGTH_LONG).show()
            UsagePermissionHelper.openUsageAccessSettings(requireContext())
        } else if (!hasOverlayPermission) {
            Toast.makeText(requireContext(), "Please grant Display over other apps permission", Toast.LENGTH_LONG).show()
            openOverlaySettings()
        }
    }

    private fun hasAllPermissions(): Boolean {
        val hasUsageStats = UsagePermissionHelper.hasUsageStatsPermission(requireContext())
        val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(requireContext())
        } else {
            true
        }
        return hasUsageStats && hasOverlayPermission
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:${requireContext().packageName}")
            startActivity(intent)
        }
    }
}
