package io.github.bect.fieldweather.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {
    @Insert
    suspend fun insertRecord(record: WeatherRecord)

    @Update
    suspend fun updateRecords(records: List<WeatherRecord>)

    @Query("SELECT * FROM weather_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<WeatherRecord>>

    @Query("SELECT * FROM weather_records WHERE isSynced = 0")
    suspend fun getUnsyncedRecords(): List<WeatherRecord>

    @Query("SELECT * FROM weather_records ORDER BY timestamp DESC LIMIT 1")
    fun getLatestRecord(): Flow<WeatherRecord?>
    
    @Query("SELECT locationName, latitude, longitude FROM weather_records WHERE locationName != '' GROUP BY locationName ORDER BY locationName ASC")
    fun getKnownLocations(): Flow<List<SavedLocation>>

    @Query("UPDATE weather_records SET isSynced = 0")
    suspend fun resetSyncStatus()

    @Query("DELETE FROM weather_records")
    suspend fun clearAllRecords()
}

data class SavedLocation(
    val locationName: String,
    val latitude: Double,
    val longitude: Double
)
