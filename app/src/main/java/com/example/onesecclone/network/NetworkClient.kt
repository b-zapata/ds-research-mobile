package com.example.onesecclone.network

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.example.onesecclone.config.AppConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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

        private const val PREF_NAME = "network_config"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SERVER_ENVIRONMENT = "server_environment"
    }

    private val preferences: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

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

    private val retrofit = Retrofit.Builder()
        .baseUrl(getBaseUrl())
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)

    fun getBaseUrl(): String {
        // First check if there's a custom URL stored
        val storedUrl = preferences.getString(KEY_BASE_URL, null)
        if (!storedUrl.isNullOrEmpty() && storedUrl != "http://35.86.154.191:8080/") {
            return storedUrl
        }

        // Otherwise use AppConfig to determine the appropriate URL
        return AppConfig.getServerUrl(context)
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

    private fun recreateRetrofit() {
        // This would require reconstructing the retrofit instance
        // For now, the app would need to be restarted for URL changes to take effect
        // In a production app, you might want to implement dynamic URL switching
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
