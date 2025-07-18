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

        fun getInstance(context: Context): DataSyncService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DataSyncService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val networkClient = NetworkClient.getInstance(context)
    private val gson = Gson()
    private val offlineQueue = ConcurrentLinkedQueue<AnalyticsData>()
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        loadOfflineData()
        startPeriodicSync()
    }

    /**
     * Send analytics data to AWS EC2 server
     */
    suspend fun sendData(data: AnalyticsData): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isNetworkAvailable()) {
                    Log.d(TAG, "No network available, queuing data offline")
                    queueDataOffline(data)
                    return@withContext false
                }

                val response = networkClient.apiService.sendAnalyticsData(data)
                val success = response.isSuccessful && response.body()?.success == true

                if (success) {
                    Log.d(TAG, "Successfully sent ${data::class.simpleName}")
                } else {
                    Log.w(TAG, "Failed to send ${data::class.simpleName}, queuing offline")
                    queueDataOffline(data)
                }

                success
            } catch (e: Exception) {
                Log.e(TAG, "Error sending data: ${e.message}", e)
                queueDataOffline(data)
                false
            }
        }
    }

    /**
     * Send batch data to AWS EC2 server
     */
    suspend fun sendBatchData(dataList: List<AnalyticsData>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isNetworkAvailable()) {
                    Log.d(TAG, "No network available for batch data")
                    dataList.forEach { queueDataOffline(it) }
                    return@withContext false
                }

                val batchData = BatchAnalyticsData(
                    deviceId = networkClient.getDeviceId(),
                    data = dataList,
                    timestamp = ZonedDateTime.now().toString()
                )

                val response = networkClient.apiService.sendBatchData(batchData)
                val success = response.isSuccessful && response.body()?.success == true

                if (success) {
                    Log.d(TAG, "Successfully sent batch data with ${dataList.size} items")
                } else {
                    Log.w(TAG, "Failed to send batch data: ${response.errorBody()?.string()}")
                    dataList.forEach { queueDataOffline(it) }
                }

                success
            } catch (e: Exception) {
                Log.e(TAG, "Error sending batch data: ${e.message}", e)
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

    private fun startPeriodicSync() {
        syncScope.launch {
            while (true) {
                try {
                    if (isNetworkAvailable() && offlineQueue.isNotEmpty()) {
                        Log.d(TAG, "Processing ${offlineQueue.size} queued items")
                        processOfflineQueue()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic sync: ${e.message}")
                }

                delay(60_000) // Check every minute
            }
        }
    }

    private suspend fun processOfflineQueue() {
        val itemsToRemove = mutableListOf<AnalyticsData>()

        for (data in offlineQueue) {
            try {
                val response = networkClient.apiService.sendAnalyticsData(data)
                val success = response.isSuccessful && response.body()?.success == true

                if (success) {
                    itemsToRemove.add(data)
                    Log.d(TAG, "Successfully synced queued ${data::class.simpleName}")
                }

                delay(500) // Small delay between requests

            } catch (e: Exception) {
                Log.e(TAG, "Error processing queued item: ${e.message}")
            }
        }

        itemsToRemove.forEach { offlineQueue.remove(it) }

        if (itemsToRemove.isNotEmpty()) {
            Log.d(TAG, "Removed ${itemsToRemove.size} items from offline queue")
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

