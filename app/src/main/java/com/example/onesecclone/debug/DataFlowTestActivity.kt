package com.example.onesecclone.debug

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.onesecclone.R
import com.example.onesecclone.analytics.AnalyticsData
import com.example.onesecclone.network.DataSyncService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

class DataFlowTestActivity : AppCompatActivity() {

    private lateinit var dataSyncService: DataSyncService
    private lateinit var tvLogOutput: TextView
    private lateinit var scrollView: ScrollView

    companion object {
        private const val TAG = "DataFlowTest"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_flow_test)

        dataSyncService = DataSyncService.getInstance(this)

        setupUI()
    }

    private fun setupUI() {
        // Find views
        val btnHealthCheck = findViewById<Button>(R.id.btnHealthCheck)
        val btnSendTestData = findViewById<Button>(R.id.btnSendTestData)
        val btnSendBatchTest = findViewById<Button>(R.id.btnSendBatchTest)
        val btnClearQueue = findViewById<Button>(R.id.btnClearQueue)
        val btnShowStatus = findViewById<Button>(R.id.btnShowStatus)
        tvLogOutput = findViewById(R.id.tvLogOutput)
        scrollView = findViewById(R.id.scrollView)

        // Set click listeners
        btnHealthCheck.setOnClickListener { performHealthCheck() }
        btnSendTestData.setOnClickListener { sendTestData() }
        btnSendBatchTest.setOnClickListener { sendBatchTestData() }
        btnClearQueue.setOnClickListener { clearOfflineQueue() }
        btnShowStatus.setOnClickListener { showCurrentStatus() }

        // Show initial status
        showCurrentStatus()
    }

    private fun performHealthCheck() {
        updateLog("🏥 Performing health check...")

        lifecycleScope.launch {
            try {
                val result = dataSyncService.performHealthCheck()

                if (result.isHealthy) {
                    updateLog("✅ Health check PASSED")
                    updateLog("   Server: ${result.serverUrl}")
                    updateLog("   Response time: ${result.responseTimeMs}ms")
                    updateLog("   Message: ${result.message}")
                    Toast.makeText(this@DataFlowTestActivity, "Server is healthy!", Toast.LENGTH_SHORT).show()
                } else {
                    updateLog("❌ Health check FAILED")
                    updateLog("   Error: ${result.message}")
                    Toast.makeText(this@DataFlowTestActivity, "Server health check failed!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                updateLog("❌ Health check exception: ${e.message}")
                Log.e(TAG, "Health check failed", e)
            }
        }
    }

    private fun sendTestData() {
        updateLog("📤 Sending test analytics data...")

        lifecycleScope.launch {
            try {
                // Create test data for each type
                val testData = listOf(
                    AnalyticsData.AppTap(
                        timestamp = ZonedDateTime.now(),
                        appName = "Test App",
                        packageName = "com.test.app"
                    ),
                    AnalyticsData.DeviceStatus(
                        batteryLevel = 85,
                        isCharging = false,
                        connectionType = "WiFi",
                        connectionStrength = "Strong",
                        appVersion = "1.0.0-test",
                        lastBatchSent = ZonedDateTime.now()
                    ),
                    AnalyticsData.Intervention(
                        interventionStart = ZonedDateTime.now().minusMinutes(2),
                        interventionEnd = ZonedDateTime.now(),
                        appName = "Test App",
                        interventionType = "video_delay",
                        videoDuration = 5000,
                        requiredWatchTime = 3000,
                        buttonClicked = "continue"
                    )
                )

                var successCount = 0
                for ((index, data) in testData.withIndex()) {
                    val eventType = when (data) {
                        is AnalyticsData.AppSession -> data.eventType
                        is AnalyticsData.AppTap -> data.eventType
                        is AnalyticsData.Intervention -> data.eventType
                        is AnalyticsData.DeviceStatus -> data.eventType
                        is AnalyticsData.DailySummary -> data.eventType
                    }
                    updateLog("   Sending $eventType (${index + 1}/${testData.size})")
                    val success = dataSyncService.sendDataWithRetry(data)

                    if (success) {
                        successCount++
                        updateLog("   ✅ $eventType sent successfully")
                    } else {
                        updateLog("   ❌ $eventType failed")
                    }

                    delay(1000) // Small delay between sends
                }

                updateLog("📊 Test complete: $successCount/${testData.size} successful")
                Toast.makeText(
                    this@DataFlowTestActivity,
                    "Test complete: $successCount/${testData.size} successful",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                updateLog("❌ Test data sending failed: ${e.message}")
                Log.e(TAG, "Failed to send test data", e)
            }
        }
    }

    private fun sendBatchTestData() {
        updateLog("📦 Sending batch test data...")

        lifecycleScope.launch {
            try {
                val batchData = listOf(
                    AnalyticsData.AppTap(
                        timestamp = ZonedDateTime.now().minusMinutes(5),
                        appName = "Batch Test 1",
                        packageName = "com.batch.test1"
                    ),
                    AnalyticsData.AppTap(
                        timestamp = ZonedDateTime.now().minusMinutes(4),
                        appName = "Batch Test 2",
                        packageName = "com.batch.test2"
                    ),
                    AnalyticsData.AppTap(
                        timestamp = ZonedDateTime.now().minusMinutes(3),
                        appName = "Batch Test 3",
                        packageName = "com.batch.test3"
                    ),
                    AnalyticsData.AppSession(
                        appName = "Batch Session Test",
                        packageName = "com.batch.session",
                        sessionStart = ZonedDateTime.now().minusMinutes(10),
                        sessionEnd = ZonedDateTime.now().minusMinutes(5)
                    )
                )

                updateLog("   Sending batch with ${batchData.size} items...")
                val success = dataSyncService.sendBatchData(batchData)

                if (success) {
                    updateLog("✅ Batch data sent successfully")
                    Toast.makeText(this@DataFlowTestActivity, "Batch sent successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    updateLog("❌ Batch data failed")
                    Toast.makeText(this@DataFlowTestActivity, "Batch send failed!", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                updateLog("❌ Batch test failed: ${e.message}")
                Log.e(TAG, "Failed to send batch test data", e)
            }
        }
    }

    private fun clearOfflineQueue() {
        updateLog("🗑️ Clearing offline queue...")

        try {
            val queueSize = dataSyncService.getQueueSize()
            dataSyncService.clearQueue()
            updateLog("✅ Cleared $queueSize items from offline queue")
            Toast.makeText(this, "Queue cleared", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            updateLog("❌ Failed to clear queue: ${e.message}")
            Log.e(TAG, "Failed to clear queue", e)
        }
    }

    private fun showCurrentStatus() {
        updateLog("📊 Current Status:")
        updateLog("   Offline queue size: ${dataSyncService.getQueueSize()}")
        updateLog("   Server URL: ${dataSyncService.networkClient.getBaseUrl()}")
        updateLog("   Device ID: ${dataSyncService.networkClient.getDeviceId()}")
    }

    private fun updateLog(message: String) {
        runOnUiThread {
            val currentText = tvLogOutput.text.toString()
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            val newText = "$currentText\n[$timestamp] $message"
            tvLogOutput.text = newText

            // Auto-scroll to bottom
            scrollView.post {
                scrollView.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }
}
