package com.fieldweather.recorder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_records")
data class WeatherRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: String, // ISO 8601 string
    val condition: String,
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    val isSynced: Boolean = false,
    val syncTimestamp: String? = null // ISO 8601 string or null
)
