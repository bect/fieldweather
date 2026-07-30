package com.fieldweather.recorder.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("FieldWeatherSettings", Context.MODE_PRIVATE)

    private val _serverUrl = MutableStateFlow(prefs.getString("pref_server_url", "http://10.0.2.2:8888") ?: "http://10.0.2.2:8888")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _timezone = MutableStateFlow(prefs.getString("pref_timezone", "Device Default") ?: "Device Default")
    val timezone: StateFlow<String> = _timezone.asStateFlow()

    private val _use24HourFormat = MutableStateFlow(prefs.getBoolean("pref_use_24_hour", false))
    val use24HourFormat: StateFlow<Boolean> = _use24HourFormat.asStateFlow()

    private val _activeLocationName = MutableStateFlow(prefs.getString("pref_loc_name", null))
    val activeLocationName: StateFlow<String?> = _activeLocationName.asStateFlow()

    private val _activeLocationLat = MutableStateFlow(prefs.getFloat("pref_loc_lat", 0f).toDouble())
    val activeLocationLat: StateFlow<Double> = _activeLocationLat.asStateFlow()

    private val _activeLocationLon = MutableStateFlow(prefs.getFloat("pref_loc_lon", 0f).toDouble())
    val activeLocationLon: StateFlow<Double> = _activeLocationLon.asStateFlow()

    fun setServerUrl(url: String) {
        prefs.edit().putString("pref_server_url", url).apply()
        _serverUrl.value = url
    }

    fun setTimezone(tz: String) {
        prefs.edit().putString("pref_timezone", tz).apply()
        _timezone.value = tz
    }

    fun setUse24HourFormat(use24: Boolean) {
        prefs.edit().putBoolean("pref_use_24_hour", use24).apply()
        _use24HourFormat.value = use24
    }

    fun setActiveLocation(name: String, lat: Double, lon: Double) {
        prefs.edit()
            .putString("pref_loc_name", name)
            .putFloat("pref_loc_lat", lat.toFloat())
            .putFloat("pref_loc_lon", lon.toFloat())
            .apply()
        _activeLocationName.value = name
        _activeLocationLat.value = lat
        _activeLocationLon.value = lon
    }

    fun clearLocation() {
        prefs.edit()
            .remove("pref_loc_name")
            .remove("pref_loc_lat")
            .remove("pref_loc_lon")
            .apply()
        _activeLocationName.value = null
        _activeLocationLat.value = 0.0
        _activeLocationLon.value = 0.0
    }

    fun getDailyColorCache(currentTimezone: String): Map<String, String> {
        val cachedTimezone = prefs.getString("cache_timezone", "")
        if (cachedTimezone != currentTimezone) {
            // Timezone changed, invalidate cache
            clearDailyColorCache()
            return emptyMap()
        }
        val cacheString = prefs.getString("cache_daily_colors", "") ?: ""
        if (cacheString.isEmpty()) return emptyMap()

        val map = mutableMapOf<String, String>()
        cacheString.split(",").forEach { pair ->
            val parts = pair.split("=")
            if (parts.size == 2) {
                map[parts[0]] = parts[1]
            }
        }
        return map
    }

    fun saveDailyColorCache(currentTimezone: String, cache: Map<String, String>) {
        val cacheString = cache.map { "${it.key}=${it.value}" }.joinToString(",")
        prefs.edit()
            .putString("cache_timezone", currentTimezone)
            .putString("cache_daily_colors", cacheString)
            .apply()
    }

    fun clearDailyColorCache() {
        prefs.edit()
            .remove("cache_timezone")
            .remove("cache_daily_colors")
            .apply()
    }
}
