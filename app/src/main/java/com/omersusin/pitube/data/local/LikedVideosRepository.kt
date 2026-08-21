package com.omersusin.pitube.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal val Context.likedVideosDataStore: DataStore<Preferences> by safePreferencesDataStore(name = "liked_videos")

class LikedVideosRepository private constructor(
    private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    private val profileManager = ProfileManager(context)
    
    companion object {
        @Volatile
        private var INSTANCE: LikedVideosRepository? = null
        
        fun getInstance(context: Context): LikedVideosRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LikedVideosRepository(
                    context.applicationContext,
                    context.likedVideosDataStore
                ).also { INSTANCE = it }
            }
        }
        
        private fun scopedKey(profileId: String, base: String) = stringPreferencesKey("${profileId}|$base")
        private fun videoKey(profileId: String, videoId: String) = scopedKey(profileId, "video_$videoId")
        private fun likeStateKey(profileId: String, videoId: String) = scopedKey(profileId, "like_state_$videoId")
        private fun orderKey(profileId: String) = scopedKey(profileId, "liked_videos_order")
        private const val LEGACY_MIGRATED_KEY = "liked_videos_scoped_v1"
        private fun legacyVideoKey(videoId: String) = stringPreferencesKey("video_$videoId")
        private fun legacyLikeStateKey(videoId: String) = stringPreferencesKey("like_state_$videoId")
        private const val LEGACY_ORDER_KEY = "liked_videos_order"
    }
    
    suspend fun ensureScopeMigration() {
        dataStore.edit { preferences ->
            if (preferences[booleanPreferencesKey(LEGACY_MIGRATED_KEY)] == true) return@edit
            val profileId = profileManager.activeProfileId.value
            if (profileId.isBlank()) return@edit
            val legacyOrder = preferences[stringPreferencesKey(LEGACY_ORDER_KEY)]
            if (!legacyOrder.isNullOrEmpty()) {
                if (preferences[orderKey(profileId)] == null) preferences[orderKey(profileId)] = legacyOrder
                preferences.remove(stringPreferencesKey(LEGACY_ORDER_KEY))
            }
            val keysToMove = preferences.asMap().keys.filter { k ->
                val n = k.name
                (n.startsWith("video_") || n.startsWith("like_state_")) && !n.contains("|")
            }.toList()
            keysToMove.forEach { key ->
                val v = preferences[key] as? String ?: return@forEach
                preferences[scopedKey(profileId, key.name)] = v
                preferences.remove(key)
            }
            preferences[booleanPreferencesKey(LEGACY_MIGRATED_KEY)] = true
        }
    }

    /**
     * Like a video — scoped to active profile
     */
    suspend fun likeVideo(videoInfo: LikedVideoInfo) {
        dataStore.edit { preferences ->
            val profileId = profileManager.activeProfileId.value
            if (profileId.isBlank()) return@edit
            preferences[videoKey(profileId, videoInfo.videoId)] = serializeVideo(videoInfo)
            preferences[likeStateKey(profileId, videoInfo.videoId)] = "LIKED"
            val currentOrder = preferences[orderKey(profileId)] ?: ""
            val orderList = if (currentOrder.isEmpty()) mutableListOf() else currentOrder.split(",").toMutableList()
            if (!orderList.contains(videoInfo.videoId)) {
                orderList.add(0, videoInfo.videoId)
                preferences[orderKey(profileId)] = orderList.joinToString(",")
            }
        }
    }
    
    /**
     * Dislike a video (removes like if exists)
     */
    suspend fun dislikeVideo(videoId: String) {
        dataStore.edit { preferences ->
            val profileId = profileManager.activeProfileId.value
            if (profileId.isBlank()) return@edit
            preferences[likeStateKey(profileId, videoId)] = "DISLIKED"
            val currentOrder = preferences[orderKey(profileId)] ?: ""
            if (currentOrder.isNotEmpty()) {
                val orderList = currentOrder.split(",").toMutableList()
                orderList.remove(videoId)
                preferences[orderKey(profileId)] = orderList.joinToString(",")
            }
        }
    }
    
    /**
     * Get like state for a video (LIKED, DISLIKED, or null)
     */
    fun getLikeState(videoId: String): Flow<String?> {
        return combine(profileManager.activeProfileId, dataStore.data) { profileId, preferences ->
            if (profileId.isBlank()) null else preferences[likeStateKey(profileId, videoId)]
        }
    }
    
    /**
     * Remove like/dislike from a video
     */
    suspend fun removeLikeState(videoId: String) {
        dataStore.edit { preferences ->
            val profileId = profileManager.activeProfileId.value
            if (profileId.isBlank()) return@edit
            preferences.remove(likeStateKey(profileId, videoId))
            val currentOrder = preferences[orderKey(profileId)] ?: ""
            if (currentOrder.isNotEmpty()) {
                val orderList = currentOrder.split(",").toMutableList()
                orderList.remove(videoId)
                preferences[orderKey(profileId)] = orderList.joinToString(",")
            }
        }
    }
    
    /**
     * Get all liked videos (mixed) — scoped to active profile
     */
    fun getAllLikedVideos(): Flow<List<LikedVideoInfo>> {
        return combine(profileManager.activeProfileId, dataStore.data) { profileId, preferences ->
            if (profileId.isBlank()) return@combine emptyList()
            val orderString = preferences[orderKey(profileId)] ?: ""
            if (orderString.isEmpty()) emptyList() else {
                val orderList = orderString.split(",")
                orderList.mapNotNull { videoId ->
                    val videoData = preferences[videoKey(profileId, videoId)]
                    videoData?.let { deserializeVideo(it) }
                }
            }
        }
    }

    fun getLikedVideosFlow(): Flow<List<LikedVideoInfo>> {
        return getAllLikedVideos().map { list -> list.filter { !it.isMusic } }
    }

    fun getLikedMusicFlow(): Flow<List<LikedVideoInfo>> {
        return getAllLikedVideos().map { list -> list.filter { it.isMusic } }
    }
    
    private fun serializeVideo(video: LikedVideoInfo): String {
        return "${video.videoId}|${video.title}|${video.thumbnail}|${video.channelName}|${video.likedAt}|${video.isMusic}"
    }
    
    private fun deserializeVideo(data: String): LikedVideoInfo? {
        return try {
            val parts = data.split("|")
            if (parts.size >= 5) {
                LikedVideoInfo(
                    videoId = parts[0],
                    title = parts[1],
                    thumbnail = parts[2],
                    channelName = parts[3],
                    likedAt = parts[4].toLong(),
                    isMusic = if (parts.size >= 6) parts[5].toBoolean() else false
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

data class LikedVideoInfo(
    val videoId: String,
    val title: String,
    val thumbnail: String,
    val channelName: String,
    val likedAt: Long = System.currentTimeMillis(),
    val isMusic: Boolean = false
)
