package com.example.onesecclone.network

import com.example.onesecclone.analytics.AnalyticsData
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("api/analytics/data")
    suspend fun sendAnalyticsData(@Body data: AnalyticsData): Response<ApiResponse>

    @POST("api/analytics/batch")
    suspend fun sendBatchData(@Body data: BatchAnalyticsData): Response<ApiResponse>

    @GET("api/health")
    suspend fun healthCheck(): Response<ApiResponse>
}

data class ApiResponse(
    val success: Boolean,
    val message: String? = null,
    val timestamp: String? = null
)

data class BatchAnalyticsData(
    val deviceId: String,
    val userId: String? = null,
    val data: List<AnalyticsData>,
    val timestamp: String
)

