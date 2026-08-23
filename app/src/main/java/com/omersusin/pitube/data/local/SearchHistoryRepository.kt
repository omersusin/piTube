package com.omersusin.pitube.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.searchDataStore: DataStore<Preferences> by safePreferencesDataStore(name = "search_history")

data class SearchHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val query: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: SearchType = SearchType.TEXT
)

enum class SearchType {
    TEXT, VOICE, SUGGESTION
}

data class SearchSuggestion(
    val text: String,
    val type: SuggestionType = SuggestionType.VIDEO
)

enum class SuggestionType {
    VIDEO, CHANNEL, PLAYLIST, TRENDING
}

data class SearchFilter(
    val contentType: ContentType = ContentType.ALL,
    val duration: Duration = Duration.ANY,
    val uploadDate: UploadDate = UploadDate.ANY,
    val sortType: SortType = SortType.RELEVANCE
)

enum class ContentType {
    ALL, VIDEOS, SHORTS, CHANNELS, PLAYLISTS, LIVE
}

enum class SortType {
   RELEVANCE, RATING, VIEWS, NEWEST
}

enum class Duration {
    ANY, UNDER_4_MINUTES, FROM_4_TO_20_MINUTES, OVER_20_MINUTES
}

enum class UploadDate {
    ANY, TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR
}

class SearchHistoryRepository(private val context: Context) {
    private val gson = Gson()
    private val profileManager = ProfileManager(context)
    private fun scopedHistoryKey(profileId: String) = stringPreferencesKey("${profileId}|search_history")
    
    companion object {
        private val SEARCH_HISTORY_KEY = stringPreferencesKey("search_history")
        private val SEARCH_HISTORY_ENABLED_KEY = booleanPreferencesKey("search_history_enabled")
        private val SEARCH_SUGGESTIONS_ENABLED_KEY = booleanPreferencesKey("search_suggestions_enabled")
        private val MAX_HISTORY_SIZE_KEY = intPreferencesKey("max_history_size")
        private val AUTO_DELETE_HISTORY_KEY = booleanPreferencesKey("auto_delete_history")
        private val HISTORY_RETENTION_DAYS_KEY = intPreferencesKey("history_retention_days")
        
        private const val DEFAULT_MAX_HISTORY_SIZE = 50
        private const val DEFAULT_RETENTION_DAYS = 90
    }
    
    private suspend fun activeScopedKey(): Preferences.Key<String>? {
        val pid = profileManager.activeProfileId.first()
        return if (pid.isBlank()) null else scopedHistoryKey(pid)
    }

    suspend fun ensureScopeMigration() {
        context.searchDataStore.edit { p ->
            if (p[SEARCH_HISTORY_KEY] == null) return@edit
            val pid = profileManager.activeProfileId.first()
            if (pid.isBlank()) return@edit
            val legacy = p[SEARCH_HISTORY_KEY] ?: return@edit
            if (p[scopedHistoryKey(pid)] == null) p[scopedHistoryKey(pid)] = legacy
            p.remove(SEARCH_HISTORY_KEY)
        }
    }

    private fun readHistory(preferences: Preferences, pid: String?): List<SearchHistoryItem> {
        val key = pid?.takeIf { it.isNotBlank() }?.let { scopedHistoryKey(it) } ?: SEARCH_HISTORY_KEY
        val json = preferences[key] ?: preferences[SEARCH_HISTORY_KEY] ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SearchHistoryItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    // Save search query — per-profile
    suspend fun saveSearchQuery(query: String, type: SearchType = SearchType.TEXT) {
        if (!isSearchHistoryEnabled()) return
        if (query.isBlank()) return
        
        context.searchDataStore.edit { preferences ->
            val pid = profileManager.activeProfileId.first()
            if (pid.isBlank()) return@edit
            val key = scopedHistoryKey(pid)
            val currentHistory = readHistory(preferences, pid)
            
            // Remove duplicate if exists
            val filteredHistory = currentHistory.filter { it.query != query }
            
            // Add new item at the beginning
            val newItem = SearchHistoryItem(
                query = query,
                type = type,
                timestamp = System.currentTimeMillis()
            )
            val updatedHistory = listOf(newItem) + filteredHistory
            
            // Trim to max size
            val maxSize = preferences[MAX_HISTORY_SIZE_KEY] ?: DEFAULT_MAX_HISTORY_SIZE
            val trimmedHistory = updatedHistory.take(maxSize)
            
            preferences[scopedHistoryKey(pid)] = gson.toJson(trimmedHistory)
            preferences.remove(SEARCH_HISTORY_KEY)
        }
    }
    
    fun getSearchHistoryFlow(): Flow<List<SearchHistoryItem>> {
        return kotlinx.coroutines.flow.combine(profileManager.activeProfileId, context.searchDataStore.data) { pid, preferences ->
            if (preferences[SEARCH_HISTORY_ENABLED_KEY] == false) emptyList()
            else filterExpiredHistory(readHistory(preferences, pid.takeIf { it.isNotBlank() }), preferences)
        }
    }
    
    // Get recent searches (limit)
    suspend fun getRecentSearches(limit: Int = 10): List<SearchHistoryItem> {
        return getSearchHistoryFlow().first().take(limit)
    }
    
    suspend fun deleteSearchItem(itemId: String) {
        context.searchDataStore.edit { preferences ->
            val pid = profileManager.activeProfileId.first()
            if (pid.isBlank()) return@edit
            val key = scopedHistoryKey(pid)
            val current = readHistory(preferences, pid)
            preferences[key] = gson.toJson(current.filter { it.id != itemId })
            preferences.remove(SEARCH_HISTORY_KEY)
        }
    }
    
    suspend fun clearSearchHistory() {
        context.searchDataStore.edit { preferences ->
            val pid = profileManager.activeProfileId.first()
            if (pid.isBlank()) return@edit
            preferences[scopedHistoryKey(pid)] = gson.toJson(emptyList<SearchHistoryItem>())
            preferences.remove(SEARCH_HISTORY_KEY)
        }
    }

    suspend fun replaceSearchHistory(items: List<SearchHistoryItem>) {
        context.searchDataStore.edit { preferences ->
            val pid = profileManager.activeProfileId.first()
            if (pid.isBlank()) return@edit
            val maxSize = preferences[MAX_HISTORY_SIZE_KEY] ?: DEFAULT_MAX_HISTORY_SIZE
            val restored = items.asSequence().filter { it.query.isNotBlank() }
                .sortedByDescending { it.timestamp }.distinctBy { it.query.trim().lowercase() }.take(maxSize).toList()
            preferences[scopedHistoryKey(pid)] = gson.toJson(restored)
            preferences.remove(SEARCH_HISTORY_KEY)
        }
    }
    
    // Settings: Enable/disable search history
    suspend fun setSearchHistoryEnabled(enabled: Boolean) {
        context.searchDataStore.edit { preferences ->
            preferences[SEARCH_HISTORY_ENABLED_KEY] = enabled
        }
    }
    
    fun isSearchHistoryEnabledFlow(): Flow<Boolean> {
        return context.searchDataStore.data.map { preferences ->
            preferences[SEARCH_HISTORY_ENABLED_KEY] ?: true
        }
    }
    
    suspend fun isSearchHistoryEnabled(): Boolean {
        return isSearchHistoryEnabledFlow().first()
    }
    
    // Settings: Enable/disable search suggestions
    suspend fun setSearchSuggestionsEnabled(enabled: Boolean) {
        context.searchDataStore.edit { preferences ->
            preferences[SEARCH_SUGGESTIONS_ENABLED_KEY] = enabled
        }
    }
    
    fun isSearchSuggestionsEnabledFlow(): Flow<Boolean> {
        return context.searchDataStore.data.map { preferences ->
            preferences[SEARCH_SUGGESTIONS_ENABLED_KEY] ?: true
        }
    }
    
    suspend fun isSearchSuggestionsEnabled(): Boolean {
        return isSearchSuggestionsEnabledFlow().first()
    }
    
    suspend fun setMaxHistorySize(size: Int) {
        context.searchDataStore.edit { preferences ->
            preferences[MAX_HISTORY_SIZE_KEY] = size
            val pid = profileManager.activeProfileId.first()
            if (pid.isBlank()) return@edit
            val key = scopedHistoryKey(pid)
            val current = readHistory(preferences, pid)
            if (current.size > size) preferences[key] = gson.toJson(current.take(size))
        }
    }
    
    fun getMaxHistorySizeFlow(): Flow<Int> {
        return context.searchDataStore.data.map { preferences ->
            preferences[MAX_HISTORY_SIZE_KEY] ?: DEFAULT_MAX_HISTORY_SIZE
        }
    }
    
    // Settings: Auto-delete history
    suspend fun setAutoDeleteHistory(enabled: Boolean) {
        context.searchDataStore.edit { preferences ->
            preferences[AUTO_DELETE_HISTORY_KEY] = enabled
        }
    }
    
    fun isAutoDeleteHistoryEnabledFlow(): Flow<Boolean> {
        return context.searchDataStore.data.map { preferences ->
            preferences[AUTO_DELETE_HISTORY_KEY] ?: false
        }
    }
    
    // Settings: History retention days
    suspend fun setHistoryRetentionDays(days: Int) {
        context.searchDataStore.edit { preferences ->
            preferences[HISTORY_RETENTION_DAYS_KEY] = days
        }
    }
    
    fun getHistoryRetentionDaysFlow(): Flow<Int> {
        return context.searchDataStore.data.map { preferences ->
            preferences[HISTORY_RETENTION_DAYS_KEY] ?: DEFAULT_RETENTION_DAYS
        }
    }

    suspend fun getSettingsBackup(): SettingsBackup {
        val preferences = context.searchDataStore.data.first()
        return SettingsBackup(
            booleans = mapOf(
                SEARCH_HISTORY_ENABLED_KEY.name to (preferences[SEARCH_HISTORY_ENABLED_KEY] ?: true),
                SEARCH_SUGGESTIONS_ENABLED_KEY.name to (preferences[SEARCH_SUGGESTIONS_ENABLED_KEY] ?: true),
                AUTO_DELETE_HISTORY_KEY.name to (preferences[AUTO_DELETE_HISTORY_KEY] ?: false)
            ),
            ints = mapOf(
                MAX_HISTORY_SIZE_KEY.name to (preferences[MAX_HISTORY_SIZE_KEY] ?: DEFAULT_MAX_HISTORY_SIZE),
                HISTORY_RETENTION_DAYS_KEY.name to (preferences[HISTORY_RETENTION_DAYS_KEY] ?: DEFAULT_RETENTION_DAYS)
            )
        )
    }

    suspend fun restoreSettings(backup: SettingsBackup) {
        context.searchDataStore.edit { preferences ->
            backup.booleans[SEARCH_HISTORY_ENABLED_KEY.name]?.let { preferences[SEARCH_HISTORY_ENABLED_KEY] = it }
            backup.booleans[SEARCH_SUGGESTIONS_ENABLED_KEY.name]?.let { preferences[SEARCH_SUGGESTIONS_ENABLED_KEY] = it }
            backup.booleans[AUTO_DELETE_HISTORY_KEY.name]?.let { preferences[AUTO_DELETE_HISTORY_KEY] = it }
            backup.ints[MAX_HISTORY_SIZE_KEY.name]?.let { preferences[MAX_HISTORY_SIZE_KEY] = it }
            backup.ints[HISTORY_RETENTION_DAYS_KEY.name]?.let { preferences[HISTORY_RETENTION_DAYS_KEY] = it }
        }
    }
    
    // Helper: Parse JSON to list
    private fun getSearchHistoryList(preferences: Preferences): List<SearchHistoryItem> {
        val json = preferences[SEARCH_HISTORY_KEY] ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SearchHistoryItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Helper: Filter expired history
    private fun filterExpiredHistory(
        history: List<SearchHistoryItem>,
        preferences: Preferences
    ): List<SearchHistoryItem> {
        val autoDelete = preferences[AUTO_DELETE_HISTORY_KEY] ?: false
        if (!autoDelete) return history
        
        val retentionDays = preferences[HISTORY_RETENTION_DAYS_KEY] ?: DEFAULT_RETENTION_DAYS
        val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        
        return history.filter { it.timestamp >= cutoffTime }
    }
    
    // Get search suggestions from YouTube API (now handled by YouTubeRepository)
    // This method is kept for backward compatibility but deprecated
    @Deprecated("Use YouTubeRepository.getSearchSuggestions() instead")
    fun getSearchSuggestions(query: String): List<SearchSuggestion> {
        if (query.isBlank()) return emptyList()
        
        // Return empty list - actual suggestions should come from YouTubeRepository
        return emptyList()
    }
}
