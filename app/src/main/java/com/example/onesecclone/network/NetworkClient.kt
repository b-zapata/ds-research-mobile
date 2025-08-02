package com.example.onesecclone.network

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.example.onesecclone.config.AppConfig
import com.example.onesecclone.analytics.AnalyticsData
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

class NetworkClient private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: NetworkClient? = null

        fun getInstance(context: Context): NetworkClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkClient(context.applicationContext).also { INSTANCE = it }
            }
        }

        // Force recreation of NetworkClient instance (useful for clearing cached URLs)
        fun resetInstance() {
            synchronized(this) {
                INSTANCE = null
            }
        }

        private const val PREF_NAME = "network_config"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SERVER_ENVIRONMENT = "server_environment"
    }

    private val preferences: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    init {
        // Immediately validate and clean cache when NetworkClient is created
        validateAndCleanCache()
    }

    private fun validateAndCleanCache() {
        try {
            // Always clear cached URL to prioritize AppConfig
            preferences.edit().remove(KEY_BASE_URL).apply()
        } catch (e: Exception) {
            // Ignore errors during initialization cleanup
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
            .header("Content-Type", "application/json")
            .header("User-Agent", "OneSecClone-Android/1.0")
            .header("X-Device-ID", getDeviceId())

        chain.proceed(requestBuilder.build())
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Custom Gson serializer for AnalyticsData sealed class
    private val analyticsDataSerializer = object : JsonSerializer<AnalyticsData> {
        override fun serialize(src: AnalyticsData, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val jsonObject = JsonObject()

            when (src) {
                is AnalyticsData.AppSession -> {
                    jsonObject.addProperty("eventType", src.eventType)
                    jsonObject.addProperty("appName", src.appName)
                    jsonObject.addProperty("packageName", src.packageName)
                    jsonObject.addProperty("sessionStart", src.sessionStart)
                    jsonObject.addProperty("sessionEnd", src.sessionEnd)
                }
                is AnalyticsData.Intervention -> {
                    jsonObject.addProperty("eventType", src.eventType)
                    jsonObject.addProperty("interventionStart", src.interventionStart)
                    jsonObject.addProperty("interventionEnd", src.interventionEnd)
                    jsonObject.addProperty("appName", src.appName)
                    jsonObject.addProperty("interventionType", src.interventionType)
                    if (src.videoDuration != null) jsonObject.addProperty("videoDuration", src.videoDuration)
                    if (src.requiredWatchTime != null) jsonObject.addProperty("requiredWatchTime", src.requiredWatchTime)
                    jsonObject.addProperty("buttonClicked", src.buttonClicked)
                }
                is AnalyticsData.DeviceStatus -> {
                    jsonObject.addProperty("eventType", src.eventType)
                    jsonObject.addProperty("batteryLevel", src.batteryLevel)
                    jsonObject.addProperty("isCharging", src.isCharging)
                    jsonObject.addProperty("connectionType", src.connectionType)
                    jsonObject.addProperty("connectionStrength", src.connectionStrength)
                    jsonObject.addProperty("appVersion", src.appVersion)
                    jsonObject.addProperty("lastBatchSent", src.lastBatchSent)
                }
                is AnalyticsData.DailySummary -> {
                    jsonObject.addProperty("eventType", src.eventType)
                    jsonObject.addProperty("date", src.date)
                    jsonObject.addProperty("totalScreenTime", src.totalScreenTime)
                    jsonObject.add("appTotals", context.serialize(src.appTotals))
                }
            }

            return jsonObject
        }
    }

    private val gson = GsonBuilder()
        .registerTypeAdapter(AnalyticsData::class.java, analyticsDataSerializer)
        .create()

    // Make retrofit mutable so we can recreate it
    private var retrofit = createRetrofit()

    val apiService: ApiService get() = retrofit.create(ApiService::class.java)

    private fun createRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    fun getBaseUrl(): String {
        // Always prioritize AppConfig URL first (this ensures build-time changes take precedence)
        val configUrl = try {
            AppConfig.getServerUrl(context)
        } catch (e: Exception) {
            null
        }

        if (!configUrl.isNullOrEmpty()) {
            // If cached URL doesn't match config, clear cache and use config
            val cachedUrl = preferences.getString(KEY_BASE_URL, null)
            if (cachedUrl != configUrl) {
                preferences.edit().remove(KEY_BASE_URL).apply()
            }
            return configUrl
        }

        // Only fall back to cached URL if no config URL is available
        val storedUrl = preferences.getString(KEY_BASE_URL, null)
        if (!storedUrl.isNullOrEmpty()) {
            return storedUrl
        }

        // If no URL is configured anywhere, throw an error
        throw IllegalStateException(
            "No server URL configured! Please configure a server URL in AppConfig. " +
            "The IP address may change frequently, so explicit configuration is required."
        )
    }

    fun setBaseUrl(url: String) {
        preferences.edit().putString(KEY_BASE_URL, url).apply()
        // Recreate retrofit instance with new URL
        recreateRetrofit()
    }

    fun setServerEnvironment(environment: AppConfig.ServerEnvironment) {
        preferences.edit().putString(KEY_SERVER_ENVIRONMENT, environment.name).apply()
        when (environment) {
            AppConfig.ServerEnvironment.CUSTOM -> {
                // Don't change URL for custom - user will set it manually
            }
            else -> {
                setBaseUrl(environment.url)
            }
        }
    }

    fun getCurrentServerEnvironment(): AppConfig.ServerEnvironment {
        val currentUrl = getBaseUrl()
        return AppConfig.ServerEnvironment.values().find { it.url == currentUrl }
            ?: AppConfig.ServerEnvironment.CUSTOM
    }

    fun clearCachedUrl() {
        preferences.edit().remove(KEY_BASE_URL).apply()
        // This will force the app to use the default configuration from AppConfig
        recreateRetrofit()
    }

    private fun recreateRetrofit() {
        retrofit = createRetrofit()
    }

    fun getDeviceId(): String {
        var deviceId = preferences.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (e: Exception) {
                "device_${System.currentTimeMillis()}"
            }
            preferences.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }
}
