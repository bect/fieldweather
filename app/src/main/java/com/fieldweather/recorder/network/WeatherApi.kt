package com.fieldweather.recorder.network

import com.fieldweather.recorder.data.WeatherRecord
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class SyncRequest(
    val client_sync_timestamp: String,
    val records: List<WeatherRecordDto>
)

data class WeatherRecordDto(
    val local_id: Int,
    val timestamp: String,
    val condition: String,
    val latitude: Double,
    val longitude: Double,
    val location_name: String
)

data class SyncResponse(
    val status: String,
    val synced_count: Int,
    val synced_ids: List<Int>?
)

interface WeatherApi {
    @POST("/sync")
    suspend fun syncWeatherRecords(@Body request: SyncRequest): Response<SyncResponse>
}
