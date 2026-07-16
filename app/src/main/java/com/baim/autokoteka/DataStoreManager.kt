package com.baim.autokoteka

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

import org.json.JSONArray
import org.json.JSONObject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "koteka_prefs")

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val rawText: String,
    val isAnomaly: Boolean,
    val anomalyReason: String,
    val status: String // "PROCESSED", "PENDING", "APPROVED", "REJECTED"
)

class DataStoreManager(private val context: Context) {

    companion object {
        val WAMENA_BULAN_INI = intPreferencesKey("wamena_bulan_ini")
        val WAMENA_TAHUN_INI = intPreferencesKey("wamena_tahun_ini")
        val YALIMO_BULAN_INI = intPreferencesKey("yalimo_bulan_ini")
        val YALIMO_TAHUN_INI = intPreferencesKey("yalimo_tahun_ini")
        val LATEST_REPORT = stringPreferencesKey("latest_report")
        
        val TARGET_BULANAN = intPreferencesKey("target_bulanan")
        val LATEST_RAW_TEXT = stringPreferencesKey("latest_raw_text")
        val LOG_HISTORY = stringPreferencesKey("log_history")
        
        val LAST_SAVED_MONTH = intPreferencesKey("last_saved_month")
        val LAST_SAVED_YEAR = intPreferencesKey("last_saved_year")
    }

    val wamenaBulanIniFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[WAMENA_BULAN_INI] ?: 39
    }
    val wamenaTahunIniFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[WAMENA_TAHUN_INI] ?: 389
    }

    val yalimoBulanIniFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[YALIMO_BULAN_INI] ?: 12
    }
    val yalimoTahunIniFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[YALIMO_TAHUN_INI] ?: 12
    }

    val targetBulananFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[TARGET_BULANAN] ?: 80
    }

    val latestReportFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LATEST_REPORT] ?: ""
    }

    val latestRawTextFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LATEST_RAW_TEXT] ?: ""
    }

    val logHistoryFlow: Flow<List<LogEntry>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[LOG_HISTORY] ?: "[]"
        parseLogHistory(jsonString)
    }

    private fun parseLogHistory(jsonString: String): List<LogEntry> {
        val list = mutableListOf<LogEntry>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    LogEntry(
                        id = obj.getLong("id"),
                        timestamp = obj.getLong("timestamp"),
                        rawText = obj.getString("rawText"),
                        isAnomaly = obj.getBoolean("isAnomaly"),
                        anomalyReason = obj.optString("anomalyReason", ""),
                        status = obj.getString("status")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.timestamp }
    }

    private fun serializeLogHistory(list: List<LogEntry>): String {
        val jsonArray = JSONArray()
        list.forEach { entry ->
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("timestamp", entry.timestamp)
            obj.put("rawText", entry.rawText)
            obj.put("isAnomaly", entry.isAnomaly)
            obj.put("anomalyReason", entry.anomalyReason)
            obj.put("status", entry.status)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    suspend fun addLogEntry(entry: LogEntry) {
        context.dataStore.edit { preferences ->
            val jsonString = preferences[LOG_HISTORY] ?: "[]"
            val list = parseLogHistory(jsonString).toMutableList()
            
            // Hapus log: 3 hari untuk yang sudah direspon, 7 hari untuk yang masih pending
            val currentTime = System.currentTimeMillis()
            val threeDaysAgo = currentTime - (3L * 24 * 60 * 60 * 1000)
            val sevenDaysAgo = currentTime - (7L * 24 * 60 * 60 * 1000)
            
            list.removeAll { entry ->
                if (entry.status == "PENDING") {
                    entry.timestamp < sevenDaysAgo
                } else {
                    entry.timestamp < threeDaysAgo
                }
            }
            
            list.add(entry)
            preferences[LOG_HISTORY] = serializeLogHistory(list)
        }
    }

    suspend fun updateLogStatus(id: Long, newStatus: String) {
        context.dataStore.edit { preferences ->
            val jsonString = preferences[LOG_HISTORY] ?: "[]"
            val list = parseLogHistory(jsonString).toMutableList()
            val index = list.indexOfFirst { it.id == id }
            if (index != -1) {
                val oldEntry = list[index]
                list[index] = oldEntry.copy(status = newStatus)
                preferences[LOG_HISTORY] = serializeLogHistory(list)
            }
        }
    }

    suspend fun setLatestRawText(rawText: String) {
        context.dataStore.edit { preferences ->
            preferences[LATEST_RAW_TEXT] = rawText
        }
    }

    suspend fun updateDataWamena(bulan: Int, tahun: Int) {
        context.dataStore.edit { preferences ->
            preferences[WAMENA_BULAN_INI] = bulan
            preferences[WAMENA_TAHUN_INI] = tahun
        }
    }

    suspend fun updateDataYalimo(bulan: Int, tahun: Int) {
        context.dataStore.edit { preferences ->
            preferences[YALIMO_BULAN_INI] = bulan
            preferences[YALIMO_TAHUN_INI] = tahun
        }
    }

    suspend fun updateTargetBulanan(target: Int) {
        context.dataStore.edit { preferences ->
            preferences[TARGET_BULANAN] = target
        }
    }

    suspend fun addAccumulation(amount: Int, isYalimo: Boolean) {
        context.dataStore.edit { preferences ->
            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH)
            val currentYear = calendar.get(Calendar.YEAR)
            
            val lastSavedMonth = preferences[LAST_SAVED_MONTH] ?: currentMonth
            val lastSavedYear = preferences[LAST_SAVED_YEAR] ?: currentYear
            
            // Logika Reset Otomatis
            if (currentYear > lastSavedYear || (currentYear == lastSavedYear && currentMonth > lastSavedMonth)) {
                // Reset bulanan
                preferences[WAMENA_BULAN_INI] = 0
                preferences[YALIMO_BULAN_INI] = 0
                
                // Reset tahunan jika ganti tahun
                if (currentYear > lastSavedYear) {
                    preferences[WAMENA_TAHUN_INI] = 0
                    preferences[YALIMO_TAHUN_INI] = 0
                }
            }
            
            preferences[LAST_SAVED_MONTH] = currentMonth
            preferences[LAST_SAVED_YEAR] = currentYear

            if (isYalimo) {
                val currentBulan = preferences[YALIMO_BULAN_INI] ?: 12
                val currentTahun = preferences[YALIMO_TAHUN_INI] ?: 12
                preferences[YALIMO_BULAN_INI] = currentBulan + amount
                preferences[YALIMO_TAHUN_INI] = currentTahun + amount
            } else {
                val currentBulan = preferences[WAMENA_BULAN_INI] ?: 39
                val currentTahun = preferences[WAMENA_TAHUN_INI] ?: 389
                preferences[WAMENA_BULAN_INI] = currentBulan + amount
                preferences[WAMENA_TAHUN_INI] = currentTahun + amount
            }
        }
    }

    suspend fun subtractAccumulation(amount: Int, isYalimo: Boolean) {
        context.dataStore.edit { preferences ->
            if (isYalimo) {
                val currentBulan = preferences[YALIMO_BULAN_INI] ?: 12
                val currentTahun = preferences[YALIMO_TAHUN_INI] ?: 12
                preferences[YALIMO_BULAN_INI] = (currentBulan - amount).coerceAtLeast(0)
                preferences[YALIMO_TAHUN_INI] = (currentTahun - amount).coerceAtLeast(0)
            } else {
                val currentBulan = preferences[WAMENA_BULAN_INI] ?: 39
                val currentTahun = preferences[WAMENA_TAHUN_INI] ?: 389
                preferences[WAMENA_BULAN_INI] = (currentBulan - amount).coerceAtLeast(0)
                preferences[WAMENA_TAHUN_INI] = (currentTahun - amount).coerceAtLeast(0)
            }
        }
    }

    suspend fun saveLatestReport(report: String) {
        context.dataStore.edit { preferences ->
            preferences[LATEST_REPORT] = report
        }
    }
}
