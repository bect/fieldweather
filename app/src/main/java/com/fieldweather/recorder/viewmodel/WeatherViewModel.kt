package com.fieldweather.recorder.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fieldweather.recorder.data.AppDatabase
import com.fieldweather.recorder.data.SavedLocation
import com.fieldweather.recorder.data.SettingsRepository
import com.fieldweather.recorder.data.WeatherRecord
import com.fieldweather.recorder.location.LocationHelper
import com.fieldweather.recorder.network.SyncRequest
import com.fieldweather.recorder.network.WeatherApi
import com.fieldweather.recorder.network.WeatherRecordDto
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.weatherDao()
    private val locationHelper = LocationHelper(application)
    
    val settings = SettingsRepository(application)

    val allRecords: StateFlow<List<WeatherRecord>> = dao.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val knownLocations = dao.getKnownLocations()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _lastSyncedDate = MutableStateFlow<String?>(null)
    val lastSyncedDate: StateFlow<String?> = _lastSyncedDate.asStateFlow()

    private val _timeUntilNextLog = MutableStateFlow<Long?>(null)
    val timeUntilNextLog: StateFlow<Long?> = _timeUntilNextLog.asStateFlow()

    val activeLocation: Flow<SavedLocation?> = combine(
        settings.activeLocationName,
        settings.activeLocationLat,
        settings.activeLocationLon
    ) { name, lat, lon ->
        if (name != null) SavedLocation(name, lat, lon) else null
    }
    
    private val cachedDayColors = java.util.concurrent.ConcurrentHashMap<java.time.LocalDate, DayColor>()
    private var isDiskCacheLoaded = false

    val thirtyDayStrip: StateFlow<List<DayColor>> = combine(allRecords, settings.timezone) { records, tzString ->
        if (!isDiskCacheLoaded) {
            val diskCache = settings.getDailyColorCache(tzString)
            diskCache.forEach { (dateStr, colorStr) ->
                try {
                    cachedDayColors[java.time.LocalDate.parse(dateStr)] = DayColor.valueOf(colorStr)
                } catch (e: Exception) {}
            }
            isDiskCacheLoaded = true
        }

        val zoneId = if (tzString == "Device Default") ZoneId.systemDefault() else ZoneId.of(tzString.replace("EST", "-05:00").replace("CST", "-06:00").replace("MST", "-07:00").replace("PST", "-08:00").replace("UTC", "UTC"))
        val today = Instant.now().atZone(zoneId).toLocalDate()
        
        val colors = mutableListOf<DayColor>()
        var cacheUpdated = false

        // Optimization: Parse and group all records by date ONCE to prevent massive GC allocations
        val recordsByDate = records.groupBy {
            Instant.parse(it.timestamp).atZone(zoneId).toLocalDate()
        }

        for (i in 29 downTo 0) {
            val targetDate = today.minusDays(i.toLong())
            
            if (targetDate.isBefore(today) && cachedDayColors.containsKey(targetDate)) {
                colors.add(cachedDayColors[targetDate]!!)
                continue
            }
            
            val dayRecords = recordsByDate[targetDate] ?: emptyList()
            
            val color = if (dayRecords.isEmpty()) {
                DayColor.NONE
            } else {
                val conditions = dayRecords.map { it.condition.lowercase() }
                if (conditions.any { it.contains("rain") || it.contains("storm") }) {
                    DayColor.RAIN
                } else if (conditions.any { it.contains("cloud") || it.contains("fog") }) {
                    DayColor.CLOUDY
                } else {
                    DayColor.SUNNY
                }
            }
            
            if (targetDate.isBefore(today)) {
                cachedDayColors[targetDate] = color
                cacheUpdated = true
            }
            colors.add(color)
        }

        if (cacheUpdated) {
            val mapToSave = cachedDayColors.entries.associate { it.key.toString() to it.value.name }
            settings.saveDailyColorCache(tzString, mapToSave)
        }
        
        colors
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.Lazily, List(30) { DayColor.NONE })

    val rainDays: StateFlow<Int> = allRecords.map { records ->
        records.count { it.condition.contains("Rain", ignoreCase = true) }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val sunnyDays: StateFlow<Int> = allRecords.map { records ->
        records.count { it.condition.lowercase() == "sunny" }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private var api: WeatherApi? = null

    init {
        // Observe server URL changes to recreate the API client
        viewModelScope.launch {
            settings.serverUrl.collectLatest { url ->
                api = createApi(url)
            }
        }

        // Load last synced date from SharedPreferences
        val prefs = application.getSharedPreferences("FieldWeatherPrefs", Context.MODE_PRIVATE)
        _lastSyncedDate.value = prefs.getString("last_synced_date", null)

        // Monitor latest record for 2-hour logic
        viewModelScope.launch {
            dao.getLatestRecord().collectLatest { latest ->
                if (latest != null) {
                    val latestTime = Instant.parse(latest.timestamp).toEpochMilli()
                    val currentTime = System.currentTimeMillis()
                    val twoHoursMillis = 2 * 60 * 60 * 1000L
                    if (currentTime - latestTime < twoHoursMillis) {
                        _timeUntilNextLog.value = twoHoursMillis - (currentTime - latestTime)
                    } else {
                        _timeUntilNextLog.value = null
                    }
                } else {
                    _timeUntilNextLog.value = null
                }
            }
        }
    }

    private fun createApi(baseUrl: String): WeatherApi? {
        return try {
            val validUrl = if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                "http://$baseUrl"
            } else baseUrl

            val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
            val client = OkHttpClient.Builder().addInterceptor(logging).build()
            Retrofit.Builder()
                .baseUrl(if (validUrl.endsWith("/")) validUrl else "$validUrl/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WeatherApi::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun setActiveLocation(location: SavedLocation) {
        settings.setActiveLocation(location.locationName, location.latitude, location.longitude)
    }

    suspend fun fetchLocation(): android.location.Location? {
        return locationHelper.getCurrentLocation()
    }

    fun saveRecord(condition: String, location: SavedLocation, observedTimeMillis: Long) {
        viewModelScope.launch {
            val timestamp = Instant.ofEpochMilli(observedTimeMillis)
                .atZone(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ISO_INSTANT)

            val record = WeatherRecord(
                timestamp = timestamp,
                condition = condition,
                latitude = location.latitude,
                longitude = location.longitude,
                locationName = location.locationName
            )
            dao.insertRecord(record)
        }
    }
    
    fun wipeLocalData() {
        viewModelScope.launch {
            dao.clearAllRecords()
            settings.clearLocation()
            _lastSyncedDate.value = null
            cachedDayColors.clear()
            getApplication<Application>().getSharedPreferences("FieldWeatherPrefs", Context.MODE_PRIVATE).edit().clear().apply()
        }
    }

    fun syncData() {
        val currentApi = api
        if (currentApi == null) {
            _syncState.value = SyncState.Error("Invalid Server URL")
            return
        }

        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            try {
                val unsynced = dao.getUnsyncedRecords()
                if (unsynced.isEmpty()) {
                    _syncState.value = SyncState.Success("No records to sync")
                    return@launch
                }

                val currentTimestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                val dtos = unsynced.map {
                    WeatherRecordDto(
                        local_id = it.id,
                        timestamp = it.timestamp,
                        condition = it.condition,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        location_name = it.locationName
                    )
                }
                
                val request = SyncRequest(
                    client_sync_timestamp = currentTimestamp,
                    records = dtos
                )

                val response = currentApi.syncWeatherRecords(request)
                if (response.isSuccessful && response.body()?.status == "success") {
                    val updatedRecords = unsynced.map { 
                        it.copy(isSynced = true, syncTimestamp = currentTimestamp) 
                    }
                    dao.updateRecords(updatedRecords)
                    
                    val prefs = getApplication<Application>().getSharedPreferences("FieldWeatherPrefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("last_synced_date", currentTimestamp).apply()
                    _lastSyncedDate.value = currentTimestamp

                    _syncState.value = SyncState.Success("Synced ${updatedRecords.size} records")
                } else {
                    _syncState.value = SyncState.Error("Server error: ${response.code()}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _syncState.value = SyncState.Error("Network error: ${e.message}")
            }
        }
    }
}

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
}

enum class DayColor {
    SUNNY, CLOUDY, RAIN, NONE
}
