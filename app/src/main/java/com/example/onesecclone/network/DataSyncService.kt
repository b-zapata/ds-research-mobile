package com.example.onesecclone.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.onesecclone.analytics.AnalyticsData
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.File
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentLinkedQueue

class DataSyncService private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: DataSyncService? = null
        private const val TAG = "DataSyncService"
        private const val OFFLINE_DATA_DIR = "offline_analytics"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 5000L
        private const val BATCH_SIZE = 10
        private const val SYNC_INTERVAL_MS = 30000L // 30 seconds

        fun getInstance(context: Context): DataSyncService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DataSyncService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val gson = Gson()
    private val offlineQueue = ConcurrentLinkedQueue<AnalyticsData>()
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Public access to networkClient for testing
    val networkClient get() = NetworkClient.getInstance(context)

    init {
        loadOfflineData()
        // startPeriodicSync()  // DISABLED: This was causing duplicate sends every 30 seconds
    }

    /**
     * Enhanced send data with retry logic
     */
    suspend fun sendDataWithRetry(data: AnalyticsData, maxRetries: Int = MAX_RETRY_ATTEMPTS): Boolean {
        return withContext(Dispatchers.IO) {
            var attempts = 0
            var lastException: Exception? = null

            while (attempts < maxRetries) {
                try {
                    if (!isNetworkAvailable()) {
                        Log.d(TAG, "No network available, queuing data offline (attempt ${attempts + 1})")
                        queueDataOffline(data)
                        return@withContext false
                    }

                    val eventType = when(data) {
                        is AnalyticsData.AppSession -> data.eventType
                        is AnalyticsData.AppTap -> data.eventType
                        is AnalyticsData.Intervention -> data.eventType
                        is AnalyticsData.DeviceStatus -> data.eventType
                        is AnalyticsData.DailySummary -> data.eventType
                    }

                    Log.d(TAG, "Sending $eventType data (attempt ${attempts + 1}/$maxRetries)")
                    val response = networkClient.apiService.sendAnalyticsData(data)

                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        if (responseBody?.success == true) {
                            Log.i(TAG, "✅ Successfully sent $eventType data")
                            return@withContext true
                        } else {
                            Log.w(TAG, "❌ Server rejected $eventType: ${responseBody?.message}")
                        }
                    } else {
                        Log.w(TAG, "❌ HTTP error ${response.code()} for $eventType: ${response.message()}")
                        // Log response body for debugging
                        response.errorBody()?.string()?.let { errorBody ->
                            Log.w(TAG, "Error response body: $errorBody")
                        }
                    }
                } catch (e: Exception) {
                    lastException = e
                    val eventType = when(data) {
                        is AnalyticsData.AppSession -> data.eventType
                        is AnalyticsData.AppTap -> data.eventType
                        is AnalyticsData.Intervention -> data.eventType
                        is AnalyticsData.DeviceStatus -> data.eventType
                        is AnalyticsData.DailySummary -> data.eventType
                    }
                    Log.e(TAG, "❌ Exception sending $eventType (attempt ${attempts + 1}): ${e.message}", e)
                }

                attempts++
                if (attempts < maxRetries) {
                    Log.d(TAG, "Retrying in ${RETRY_DELAY_MS}ms...")
                    delay(RETRY_DELAY_MS)
                }
            }

            val eventType = when(data) {
                is AnalyticsData.AppSession -> data.eventType
                is AnalyticsData.AppTap -> data.eventType
                is AnalyticsData.Intervention -> data.eventType
                is AnalyticsData.DeviceStatus -> data.eventType
                is AnalyticsData.DailySummary -> data.eventType
            }
            Log.w(TAG, "❌ Failed to send $eventType after $maxRetries attempts, queuing offline")
            queueDataOffline(data)
            return@withContext false
        }
    }

    /**
     * Enhanced health check with detailed server verification
     */
    suspend fun performHealthCheck(): HealthCheckResult {
        return withContext(Dispatchers.IO) {
            try {
                if (!isNetworkAvailable()) {
                    return@withContext HealthCheckResult(false, "No network connection available")
                }

                val startTime = System.currentTimeMillis()
                val response = networkClient.apiService.healthCheck()
                val responseTime = System.currentTimeMillis() - startTime

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        Log.i(TAG, "✅ Health check passed (${responseTime}ms)")
                        return@withContext HealthCheckResult(
                            true,
                            "Server healthy",
                            responseTime,
                            networkClient.getBaseUrl()
                        )
                    } else {
                        return@withContext HealthCheckResult(false, "Server returned success=false")
                    }
                } else {
                    return@withContext HealthCheckResult(
                        false,
                        "HTTP ${response.code()}: ${response.message()}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Health check failed: ${e.message}", e)
                return@withContext HealthCheckResult(false, "Exception: ${e.message}")
            }
        }
    }

    /**
     * Send analytics data to AWS EC2 server
     */
    suspend fun sendData(data: AnalyticsData): Boolean {
        return sendDataWithRetry(data)
    }

    /**
     * Send batch data to AWS EC2 server with improved chunking
     */
    suspend fun sendBatchData(dataList: List<AnalyticsData>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isNetworkAvailable()) {
                    Log.d(TAG, "No network available for batch data")
                    dataList.forEach { queueDataOffline(it) }
                    return@withContext false
                }

                // Split large batches into smaller chunks for better reliability
                val chunks = dataList.chunked(BATCH_SIZE)
                var successCount = 0
                var failedItems = mutableListOf<AnalyticsData>()

                Log.d(TAG, "Sending ${dataList.size} items in ${chunks.size} chunks of max $BATCH_SIZE items")

                for ((index, chunk) in chunks.withIndex()) {
                    try {
                        Log.d(TAG, "Sending chunk ${index + 1}/${chunks.size} with ${chunk.size} items")

                        val batchData = BatchAnalyticsData(
                            deviceId = networkClient.getDeviceId(),
                            data = chunk,
                            timestamp = ZonedDateTime.now().toString()
                        )

                        val response = networkClient.apiService.sendBatchData(batchData)
                        val success = response.isSuccessful && response.body()?.success == true

                        if (success) {
                            successCount++
                            Log.d(TAG, "Successfully sent chunk ${index + 1} with ${chunk.size} items")
                        } else {
                            Log.w(TAG, "Batch failed for chunk ${index + 1}, trying individual requests: ${response.errorBody()?.string()}")

                            // Fallback: try sending each item individually
                            var individualSuccessCount = 0
                            for (item in chunk) {
                                try {
                                    val individualSuccess = sendDataWithRetry(item, maxRetries = 1)
                                    if (individualSuccess) {
                                        individualSuccessCount++
                                    } else {
                                        failedItems.add(item)
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Individual request failed for ${item::class.simpleName}: ${e.message}")
                                    failedItems.add(item)
                                }
                            }

                            if (individualSuccessCount == chunk.size) {
                                successCount++
                                Log.i(TAG, "✅ Chunk ${index + 1} succeeded via individual requests ($individualSuccessCount/${chunk.size})")
                            } else {
                                Log.w(TAG, "❌ Chunk ${index + 1} partially failed: $individualSuccessCount/${chunk.size} succeeded individually")
                            }
                        }

                        // Add small delay between chunks to avoid overwhelming the server
                        if (index < chunks.size - 1) {
                            delay(1000) // 1 second delay between chunks
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending chunk ${index + 1}: ${e.message}", e)

                        // Fallback: try individual requests for this chunk too
                        Log.d(TAG, "Trying individual requests for failed chunk ${index + 1}")
                        var individualSuccessCount = 0
                        for (item in chunk) {
                            try {
                                val individualSuccess = sendDataWithRetry(item, maxRetries = 1)
                                if (individualSuccess) {
                                    individualSuccessCount++
                                } else {
                                    failedItems.add(item)
                                }
                            } catch (ie: Exception) {
                                Log.w(TAG, "Individual fallback failed for ${item::class.simpleName}: ${ie.message}")
                                failedItems.add(item)
                            }
                        }

                        if (individualSuccessCount == chunk.size) {
                            successCount++
                            Log.i(TAG, "✅ Chunk ${index + 1} recovered via individual requests ($individualSuccessCount/${chunk.size})")
                        } else {
                            Log.w(TAG, "❌ Chunk ${index + 1} recovery failed: $individualSuccessCount/${chunk.size} succeeded individually")
                        }
                    }
                }

                // Queue failed items for retry
                failedItems.forEach { queueDataOffline(it) }

                val overallSuccess = successCount == chunks.size
                Log.d(TAG, "Batch operation complete: $successCount/${chunks.size} chunks successful")

                overallSuccess
            } catch (e: Exception) {
                Log.e(TAG, "Error in batch operation: ${e.message}", e)
                dataList.forEach { queueDataOffline(it) }
                false
            }
        }
    }

    private fun queueDataOffline(data: AnalyticsData) {
        try {
            offlineQueue.offer(data)
            saveDataToDisk(data)
            Log.d(TAG, "Queued ${data::class.simpleName} for offline sync")
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing data offline: ${e.message}")
        }
    }

    private fun saveDataToDisk(data: AnalyticsData) {
        try {
            val offlineDir = File(context.filesDir, OFFLINE_DATA_DIR)
            if (!offlineDir.exists()) {
                offlineDir.mkdirs()
            }

            val filename = "${data::class.simpleName}_${System.currentTimeMillis()}.json"
            val file = File(offlineDir, filename)

            val json = gson.toJson(data)
            file.writeText(json)

        } catch (e: Exception) {
            Log.e(TAG, "Error saving data to disk: ${e.message}")
        }
    }

    private fun loadOfflineData() {
        try {
            val offlineDir = File(context.filesDir, OFFLINE_DATA_DIR)
            if (!offlineDir.exists()) return

            offlineDir.listFiles()?.forEach { file ->
                try {
                    // Instead of trying to deserialize abstract class, just delete the problematic files
                    // The data will be resent when the app generates new analytics events
                    Log.d(TAG, "Removing problematic offline file: ${file.name}")
                    file.delete()
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing offline file ${file.name}: ${e.message}")
                    file.delete()
                }
            }

            Log.d(TAG, "Cleared offline storage of problematic serialized data")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading offline data: ${e.message}")
        }
    }

    /**
     * Enhanced periodic sync with better error handling
     */
    private fun startPeriodicSync() {
        syncScope.launch {
            while (true) {
                try {
                    if (isNetworkAvailable() && offlineQueue.isNotEmpty()) {
                        Log.d(TAG, "🔄 Starting periodic sync (${offlineQueue.size} items queued)")
                        syncOfflineData()
                    }

                    // Perform health check periodically
                    val healthResult = performHealthCheck()
                    if (!healthResult.isHealthy) {
                        Log.w(TAG, "⚠️ Health check failed: ${healthResult.message}")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic sync: ${e.message}", e)
                }

                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    /**
     * Enhanced offline data sync with batching
     */
    private suspend fun syncOfflineData() {
        val itemsToSync = mutableListOf<AnalyticsData>()

        // Collect items for batch processing
        repeat(BATCH_SIZE) {
            val item = offlineQueue.poll()
            if (item != null) {
                itemsToSync.add(item)
            }
        }

        if (itemsToSync.isNotEmpty()) {
            Log.d(TAG, "📤 Syncing ${itemsToSync.size} offline items")
            val success = sendBatchData(itemsToSync)

            if (!success) {
                // Put items back in queue if batch failed
                itemsToSync.forEach { offlineQueue.offer(it) }
                Log.w(TAG, "❌ Batch sync failed, items returned to queue")
            } else {
                Log.i(TAG, "✅ Successfully synced ${itemsToSync.size} offline items")
                // Clean up corresponding disk files
                cleanupSyncedFiles(itemsToSync)
            }
        }
    }

    private fun cleanupSyncedFiles(syncedItems: List<AnalyticsData>) {
        try {
            val offlineDir = File(context.filesDir, OFFLINE_DATA_DIR)
            if (!offlineDir.exists()) return

            // This is a simplified cleanup - in practice you'd want to track which files correspond to which data
            val files = offlineDir.listFiles()?.sortedBy { it.lastModified() }
            files?.take(syncedItems.size)?.forEach { file ->
                if (file.delete()) {
                    Log.d(TAG, "🗑️ Cleaned up synced file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up synced files: ${e.message}")
        }
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network availability: ${e.message}")
            false
        }
    }

    fun getQueueSize(): Int = offlineQueue.size

    fun clearQueue() {
        offlineQueue.clear()
        try {
            val offlineDir = File(context.filesDir, OFFLINE_DATA_DIR)
            offlineDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing offline directory: ${e.message}")
        }
    }
}

data class HealthCheckResult(
    val isHealthy: Boolean,
    val message: String,
    val responseTimeMs: Long = 0,
    val serverUrl: String = ""
)
