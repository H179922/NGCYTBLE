package com.ngcyt.ble.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ngcytble_settings")

enum class LocationMode { WIFI_DERIVED, GPS, FUSED, PASSIVE }

class AppSettings(private val dataStore: DataStore<Preferences>) {

    companion object {
        val IGNORE_MACS = stringSetPreferencesKey("ignore_macs")
        val MIN_ALERT_SCORE = intPreferencesKey("min_alert_score")
        val SCAN_INTERVAL_SECONDS = intPreferencesKey("scan_interval_seconds")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val LOCATION_MODE = stringPreferencesKey("location_mode")
        val EXTERNAL_API_URL = stringPreferencesKey("external_api_url")
        val EXTERNAL_API_KEY = stringPreferencesKey("external_api_key")
        val EXTERNAL_API_MODE = stringPreferencesKey("external_api_mode")
        val COMPANION_PI_URL = stringPreferencesKey("companion_pi_url")
        val COMPANION_POLL_INTERVAL = intPreferencesKey("companion_poll_interval")
    }

    val ignoreMacs: Flow<Set<String>> = dataStore.data.map { it[IGNORE_MACS] ?: emptySet() }
    val minAlertScore: Flow<Int> = dataStore.data.map { it[MIN_ALERT_SCORE] ?: 40 }
    val scanIntervalSeconds: Flow<Int> = dataStore.data.map { it[SCAN_INTERVAL_SECONDS] ?: 60 }
    val retentionDays: Flow<Int> = dataStore.data.map { it[RETENTION_DAYS] ?: 30 }
    val locationMode: Flow<LocationMode> = dataStore.data.map {
        try { LocationMode.valueOf(it[LOCATION_MODE] ?: "WIFI_DERIVED") } catch (_: Exception) { LocationMode.WIFI_DERIVED }
    }
    val externalApiUrl: Flow<String?> = dataStore.data.map { it[EXTERNAL_API_URL] }
    val externalApiKey: Flow<String?> = dataStore.data.map { it[EXTERNAL_API_KEY] }
    val externalApiMode: Flow<String> = dataStore.data.map { it[EXTERNAL_API_MODE] ?: "ALERTS_ONLY" }
    val companionPiUrl: Flow<String?> = dataStore.data.map { it[COMPANION_PI_URL] }
    val companionPollInterval: Flow<Int> = dataStore.data.map { it[COMPANION_POLL_INTERVAL] ?: 30 }

    suspend fun setIgnoreMacs(macs: Set<String>) { dataStore.edit { it[IGNORE_MACS] = macs } }
    suspend fun addIgnoreMac(mac: String) { dataStore.edit { it[IGNORE_MACS] = (it[IGNORE_MACS] ?: emptySet()) + mac } }
    suspend fun removeIgnoreMac(mac: String) { dataStore.edit { it[IGNORE_MACS] = (it[IGNORE_MACS] ?: emptySet()) - mac } }
    suspend fun setMinAlertScore(score: Int) { dataStore.edit { it[MIN_ALERT_SCORE] = score } }
    suspend fun setScanInterval(seconds: Int) { dataStore.edit { it[SCAN_INTERVAL_SECONDS] = seconds } }
    suspend fun setRetentionDays(days: Int) { dataStore.edit { it[RETENTION_DAYS] = days } }
    suspend fun setLocationMode(mode: LocationMode) { dataStore.edit { it[LOCATION_MODE] = mode.name } }
    suspend fun setExternalApi(url: String?, key: String?, mode: String?) {
        dataStore.edit { prefs ->
            if (url != null) prefs[EXTERNAL_API_URL] = url else prefs.remove(EXTERNAL_API_URL)
            if (key != null) prefs[EXTERNAL_API_KEY] = key else prefs.remove(EXTERNAL_API_KEY)
            if (mode != null) prefs[EXTERNAL_API_MODE] = mode
        }
    }
    suspend fun setCompanionPi(url: String?, intervalSeconds: Int? = null) {
        dataStore.edit { prefs ->
            if (url != null) prefs[COMPANION_PI_URL] = url else prefs.remove(COMPANION_PI_URL)
            if (intervalSeconds != null) prefs[COMPANION_POLL_INTERVAL] = intervalSeconds
        }
    }
}
