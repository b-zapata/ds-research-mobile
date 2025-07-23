package com.example.onesecclone.config

import android.content.Context
import com.example.onesecclone.BuildConfig

/**
 * Central configuration class for managing app settings
 * This allows easy management of server URLs and other configuration
 */
object AppConfig {

    // Default fallback URLs - these can be easily updated
    const val DEFAULT_PRODUCTION_URL = "http://54.149.247.183:8080/"
    const val DEFAULT_STAGING_URL = "http://54.149.247.183:8080/"
    const val DEFAULT_LOCAL_URL = "http://54.149.247.183:8080/"

    // Environment variable names (can be set via build configuration)
    const val ENV_SERVER_URL = "SERVER_URL"
    const val ENV_API_KEY = "API_KEY"

    /**
     * Gets the appropriate server URL based on build type and configuration
     */
    fun getServerUrl(context: Context): String {
        // 1. Check build config for environment variables first
        val envUrl = getBuildConfigString("SERVER_URL")
        if (!envUrl.isNullOrEmpty()) {
            return envUrl
        }

        // 2. Use default based on build type
        return when {
            BuildConfig.DEBUG -> DEFAULT_LOCAL_URL
            BuildConfig.BUILD_TYPE == "staging" -> DEFAULT_STAGING_URL
            else -> DEFAULT_PRODUCTION_URL
        }
    }

    /**
     * Get build config string value safely
     */
    private fun getBuildConfigString(fieldName: String): String? {
        return try {
            val buildConfigClass = Class.forName("com.example.onesecclone.BuildConfig")
            val field = buildConfigClass.getDeclaredField(fieldName)
            field.get(null) as? String
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Predefined server configurations for easy switching
     */
    enum class ServerEnvironment(val displayName: String, val url: String) {
        PRODUCTION("Production", DEFAULT_PRODUCTION_URL),
        STAGING("Staging", DEFAULT_STAGING_URL),
        LOCAL("Local Development", DEFAULT_LOCAL_URL),
        CUSTOM("Custom", "")
    }
}
