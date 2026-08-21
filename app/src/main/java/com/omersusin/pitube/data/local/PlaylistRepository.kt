package com.omersusin.pitube.data.local

import com.omersusin.pitube.data.local.dao.PlaylistDao
import com.omersusin.pitube.data.local.dao.PlaylistWithCount
import com.omersusin.pitube.data.local.dao.VideoDao
import com.omersusin.pitube.data.local.entity.PlaylistEntity
import com.omersusin.pitube.data.local.entity.PlaylistVideoCrossRef
import com.omersusin.pitube.data.local.entity.VideoEntity
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.ui.screens.playlists.PlaylistInfo
import com.omersusin.pitube.utils.parseRelativeToTimestamp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val videoDao: VideoDao,
    private val context: android.content.Context
) {
    constructor(context: android.content.Context) : this(
        AppDatabase.getDatabase(context).playlistDao(),
        AppDatabase.getDatabase(context).videoDao(),
        context.applicationContext
    )

    private val profileManager by lazy { ProfileManager(context) }

    /** Active profile id; blank when signed-out (rows stay legacy device-wide). */
    private suspend fun pid(): String =
        try { profileManager.activeProfileId.first() } catch (_: Exception) { "" }

    /**
     * Per-profile system playlist ids. Same base id is namespaced per profile so
     * each account has its own Watch Later / Saved Shorts. Legacy un-suffixed rows
     * are adopted at startup via [ensureScopeMigration].
     */
    companion object {
        const val WATCH_LATER_ID = "watch_later"
        const val SAVED_SHORTS_ID = "saved_shorts"
        fun scopedId(base: String, profileId: String) =
            if (profileId.isBlank()) base else "$base@$profileId"
    }

    private suspend fun watchLaterId(): String = scopedId(WATCH_LATER_ID, pid())
    private suspend fun savedShortsId(): String = scopedId(SAVED_SHORTS_ID, pid())

    /**
     * One-time adoption of pre-scope rows into the active profile:
     * stamps profileId on legacy rows and renames the system playlists to their
     * per-profile ids (moving cross-refs along). Nothing stays device-wide.
     */
    suspend fun ensureScopeMigration() {
        val active = pid()
        if (active.isBlank()) return
        playlistDao.adoptLegacyRows(active)
        if (playlistDao.getPlaylist(WATCH_LATER_ID, active) != null &&
            playlistDao.getPlaylist(scopedId(WATCH_LATER_ID, active), active) == null) {
            playlistDao.renamePlaylistId(WATCH_LATER_ID, scopedId(WATCH_LATER_ID, active), active)
        }
        if (playlistDao.getPlaylist(SAVED_SHORTS_ID, active) != null &&
            playlistDao.getPlaylist(scopedId(SAVED_SHORTS_ID, active), active) == null) {
            playlistDao.renamePlaylistId(SAVED_SHORTS_ID, scopedId(SAVED_SHORTS_ID, active), active)
        }
    }

    suspend fun updateVideoMetadata(video: Video) {
        val normalizedVideo = parseRelativeToTimestamp(video.uploadDate)
            ?.let { parsedTimestamp ->
                val stableTimestamp = video.timestamp.takeIf { it > 0L }
                    ?.let { minOf(it, parsedTimestamp) }
                    ?: parsedTimestamp
                video.copy(timestamp = stableTimestamp)
            }
            ?: video
        val entity = VideoEntity.fromDomain(normalizedVideo)
        videoDao.insertVideoOrIgnore(entity)
        videoDao.updateVideoMetadata(
            id = entity.id,
            title = entity.title,
            channelName = entity.channelName,
            channelId = entity.channelId,
            thumbnailUrl = entity.thumbnailUrl,
            duration = entity.duration,
            viewCount = entity.viewCount,
            uploadDate = entity.uploadDate,
            timestamp = entity.timestamp,
            description = entity.description,
            channelThumbnailUrl = entity.channelThumbnailUrl
        )
    }

    // Saved Shorts Logic
    suspend fun addToSavedShorts(video: Video) {
        val shortsId = savedShortsId()
        // Ensure saved shorts playlist exists
        val savedShorts = playlistDao.getPlaylist(shortsId, pid())
        if (savedShorts == null) {
            playlistDao.insertPlaylist(
                PlaylistEntity(
                    id = shortsId,
                    name = "Saved Shorts",
                    description = "Your saved shorts",
                    thumbnailUrl = "",
                    isPrivate = true,
                    createdAt = System.currentTimeMillis(),
                    profileId = pid()
                )
            )
        }

        // Save video
        updateVideoMetadata(video)

        // Add relationship
        val position = System.currentTimeMillis()
        playlistDao.insertPlaylistVideoCrossRef(
            PlaylistVideoCrossRef(
                playlistId = shortsId,
                videoId = video.id,
                position = -position
            )
        )
    }

    suspend fun removeFromSavedShorts(videoId: String) {
        playlistDao.removeVideoFromPlaylist(savedShortsId(), videoId)
    }

    fun getSavedShortsFlow(): Flow<List<Video>> =
        profileManager.activeProfileId.flatMapLatest { p ->
            playlistDao.getVideosForPlaylist(scopedId(SAVED_SHORTS_ID, p)).map { entities ->
                entities.map { it.toDomain() }
            }
        }

    fun getVideoOnlySavedShortsFlow(): Flow<List<Video>> =
        getSavedShortsFlow().map { list -> list.filter { !it.isMusic } }

    suspend fun isInSavedShorts(videoId: String): Boolean {
        val videos = playlistDao.getVideosForPlaylist(savedShortsId()).firstOrNull() ?: emptyList()
        return videos.any { it.id == videoId }
    }

    suspend fun addToWatchLater(video: Video) {
        try {
            val wlId = watchLaterId()
            android.util.Log.d("PlaylistRepository", "Adding video to Watch Later: ${video.id}")
            val watchLater = playlistDao.getPlaylist(wlId, pid())
            if (watchLater == null) {
                android.util.Log.d("PlaylistRepository", "Creating Watch Later playlist")
                playlistDao.insertPlaylist(
                    PlaylistEntity(
                        id = wlId,
                        name = "Watch Later",
                        description = "Your watch later list",
                        thumbnailUrl = "",
                        isPrivate = true,
                        createdAt = System.currentTimeMillis(),
                        profileId = pid()
                    )
                )
            }

            // Save video
            android.util.Log.d("PlaylistRepository", "Inserting video metadata")
            updateVideoMetadata(video)

            // Add relationship
            val position = System.currentTimeMillis()
            android.util.Log.d("PlaylistRepository", "Inserting cross-ref")
            playlistDao.insertPlaylistVideoCrossRef(
                PlaylistVideoCrossRef(
                    playlistId = wlId,
                    videoId = video.id,
                    position = -position
                )
            )
            android.util.Log.d("PlaylistRepository", "Successfully added to Watch Later")
        } catch (e: Exception) {
            android.util.Log.e("PlaylistRepository", "Failed to add to Watch Later", e)
            throw e
        }
    }

    suspend fun removeFromWatchLater(videoId: String) {
        playlistDao.removeVideoFromPlaylist(watchLaterId(), videoId)
    }

    suspend fun clearWatchLater() {
        playlistDao.deletePlaylist(watchLaterId(), pid())
    }

    fun getWatchLaterVideosFlow(): Flow<List<Video>> =
        profileManager.activeProfileId.flatMapLatest { p ->
            playlistDao.getVideosForPlaylist(scopedId(WATCH_LATER_ID, p)).map { entities ->
                entities.map { it.toDomain() }
            }
        }

    fun getVideoOnlyWatchLaterFlow(): Flow<List<Video>> =
        getWatchLaterVideosFlow().map { list -> list.filter { !it.isMusic } }

    fun getMusicOnlyWatchLaterFlow(): Flow<List<Video>> =
        getWatchLaterVideosFlow().map { list -> list.filter { it.isMusic } }

    fun getWatchLaterIdsFlow(): Flow<Set<String>> =
        profileManager.activeProfileId.flatMapLatest { p ->
            playlistDao.getVideosForPlaylist(scopedId(WATCH_LATER_ID, p)).map { entities ->
                entities.map { it.id }.toSet()
            }
        }

    fun isVideoSavedToAnyPlaylistFlow(videoId: String): Flow<Boolean> =
        profileManager.activeProfileId.flatMapLatest { p ->
            playlistDao.getVideoPlaylistMembershipCount(videoId, p).map { it > 0 }
        }

    suspend fun isInWatchLater(videoId: String): Boolean {
        return try {
            playlistDao.isVideoInPlaylist(watchLaterId(), videoId) > 0
        } catch (e: Exception) {
            android.util.Log.e("PlaylistRepository", "Error checking watch later status", e)
            false
        }
    }

    suspend fun isVideoInPlaylist(playlistId: String, videoId: String): Boolean {
        return try {
            playlistDao.isVideoInPlaylist(playlistId, videoId) > 0
        } catch (e: Exception) {
            android.util.Log.e("PlaylistRepository", "Error checking playlist status", e)
            false
        }
    }

    // Playlist Management
    suspend fun createPlaylist(playlistId: String, name: String, description: String, isPrivate: Boolean, isMusic: Boolean = false) {
        val entity = PlaylistEntity(
            id = playlistId,
            name = name,
            description = description,
            thumbnailUrl = "",
            isPrivate = isPrivate,
            createdAt = System.currentTimeMillis(),
            isMusic = isMusic,
            isUserCreated = true,
            profileId = pid()
        )
        playlistDao.insertPlaylist(entity)
    }

    suspend fun saveExternalVideoPlaylist(id: String, name: String, description: String, thumbnailUrl: String) {
        val entity = PlaylistEntity(
            id = id,
            name = name,
            description = description,
            thumbnailUrl = thumbnailUrl,
            isPrivate = false,
            createdAt = System.currentTimeMillis(),
            isMusic = false,
            isUserCreated = false,
            profileId = pid()
        )
        playlistDao.insertPlaylist(entity)
    }

    suspend fun saveExternalMusicPlaylist(id: String, name: String, description: String, thumbnailUrl: String) {
        val entity = PlaylistEntity(
            id = id,
            name = name,
            description = description,
            thumbnailUrl = thumbnailUrl,
            isPrivate = false,
            createdAt = System.currentTimeMillis(),
            isMusic = true,
            isUserCreated = false,
            profileId = pid()
        )
        playlistDao.insertPlaylist(entity)
    }

    suspend fun unsaveExternalPlaylist(playlistId: String) {
        val p = pid()
        val entity = playlistDao.getPlaylist(playlistId, p)
        if (entity != null && !entity.isUserCreated) {
            playlistDao.deletePlaylist(playlistId, p)
        }
    }

    suspend fun isExternalPlaylistSaved(playlistId: String): Boolean {
        return playlistDao.isSavedExternalPlaylist(playlistId, pid()) > 0
    }

    suspend fun updatePlaylistName(playlistId: String, name: String) {
        playlistDao.updatePlaylistName(playlistId, name, pid())
    }

    suspend fun deletePlaylist(playlistId: String) {
        playlistDao.deletePlaylist(playlistId, pid())
    }

    suspend fun addVideoToPlaylist(playlistId: String, video: Video) {
        try {
            android.util.Log.d("PlaylistRepository", "Adding video ${video.id} to playlist $playlistId")
            // Save video first
            updateVideoMetadata(video)
            
            // Add relation
            val position = -System.currentTimeMillis()
            playlistDao.insertPlaylistVideoCrossRef(
                PlaylistVideoCrossRef(
                    playlistId = playlistId,
                    videoId = video.id,
                    position = position
                )
            )

            val newThumb = playlistDao.getFirstVideoThumbnail(playlistId) ?: video.thumbnailUrl
            playlistDao.updatePlaylistThumbnail(playlistId, newThumb, pid())
            android.util.Log.d("PlaylistRepository", "Successfully added to playlist $playlistId")
        } catch (e: Exception) {
            android.util.Log.e("PlaylistRepository", "Failed to add to playlist $playlistId", e)
            throw e
        }
    }

    suspend fun addVideosToPlaylist(targetPlaylistId: String, videos: List<Video>) {
        videos.forEach { video -> addVideoToPlaylist(targetPlaylistId, video) }
    }

    suspend fun removeVideoFromPlaylist(playlistId: String, videoId: String) {
        playlistDao.removeVideoFromPlaylist(playlistId, videoId)
        val newThumb = playlistDao.getFirstVideoThumbnail(playlistId) ?: ""
        playlistDao.updatePlaylistThumbnail(playlistId, newThumb, pid())
    }

    suspend fun reorderVideosInPlaylist(playlistId: String, orderedVideoIds: List<String>) {
        orderedVideoIds.forEachIndexed { index, videoId ->
            playlistDao.updatePlaylistVideoPosition(
                playlistId = playlistId,
                videoId = videoId,
                position = index.toLong()
            )
        }
        val newThumb = playlistDao.getFirstVideoThumbnail(playlistId) ?: ""
        playlistDao.updatePlaylistThumbnail(playlistId, newThumb, pid())
    }

    private fun scopedPlaylistsWithCount(
        query: (String) -> Flow<List<PlaylistWithCount>>
    ): Flow<List<PlaylistInfo>> =
        profileManager.activeProfileId.flatMapLatest { p ->
            query(p).map { items ->
                items.map { item ->
                    PlaylistInfo(
                        id = item.playlist.id,
                        name = item.playlist.name,
                        description = item.playlist.description,
                        videoCount = item.videoCount,
                        thumbnailUrl = item.playlist.thumbnailUrl,
                        isPrivate = item.playlist.isPrivate,
                        createdAt = item.playlist.createdAt
                    )
                }
            }
        }

    fun getAllPlaylistsFlow(): Flow<List<PlaylistInfo>> = scopedPlaylistsWithCount { playlistDao.getAllPlaylistsWithCount(it) }

    fun getUserCreatedVideoPlaylistsFlow(): Flow<List<PlaylistInfo>> = scopedPlaylistsWithCount { playlistDao.getUserCreatedVideoPlaylistsWithCount(it) }

    fun getSavedVideoPlaylistsFlow(): Flow<List<PlaylistInfo>> = scopedPlaylistsWithCount { playlistDao.getSavedVideoPlaylistsWithCount(it) }

    fun getMusicPlaylistsFlow(): Flow<List<PlaylistInfo>> = scopedPlaylistsWithCount { playlistDao.getMusicPlaylistsWithCount(it) }

    fun getUserCreatedMusicPlaylistsFlow(): Flow<List<PlaylistInfo>> = scopedPlaylistsWithCount { playlistDao.getUserCreatedMusicPlaylistsWithCount(it) }

    fun getSavedMusicPlaylistsFlow(): Flow<List<PlaylistInfo>> = scopedPlaylistsWithCount { playlistDao.getSavedMusicPlaylistsWithCount(it) }

    suspend fun getSavedVideoPlaylistVideos(): List<Video> =
        playlistDao.getSavedVideoPlaylistVideos(pid()).map { it.toDomain() }

    fun getPlaylistVideosFlow(playlistId: String): Flow<List<Video>> =
        playlistDao.getVideosForPlaylist(playlistId).map { entities ->
            entities.map { it.toDomain() }
        }

    /** Like [getPlaylistVideosFlow] but each video carries when it was added to this playlist. */
    fun getPlaylistVideosWithAddedAtFlow(playlistId: String): Flow<List<Video>> =
        playlistDao.getVideosWithMetaForPlaylist(playlistId).map { rows ->
            // addedAt <= 0 means "unknown" (legacy rows reordered before the addedAt column
            // existed) — surface null so the UI falls back instead of showing an epoch date.
            rows.map { it.video.toDomain().copy(addedAtInPlaylist = it.addedAt.takeIf { ts -> ts > 0L }) }
        }

    /**
     * Reconciles a saved (not-owned) playlist's local copy with a fresh remote fetch: upserts each
     * remote video's metadata, restores creator order, adds newly-published videos and drops ones
     * the creator removed. Keeps the playlist available offline while showing real, current data.
     */
    suspend fun syncSavedPlaylistVideos(playlistId: String, remoteVideos: List<Video>) {
        if (remoteVideos.isEmpty()) return
        val remoteIds = remoteVideos.mapTo(HashSet()) { it.id }
        val existingIds = playlistDao.getVideosForPlaylist(playlistId).firstOrNull()
            ?.map { it.id }?.toSet() ?: emptySet()

        remoteVideos.forEachIndexed { index, video ->
            updateVideoMetadata(video)
            playlistDao.insertPlaylistVideoCrossRef(
                PlaylistVideoCrossRef(
                    playlistId = playlistId,
                    videoId = video.id,
                    position = index.toLong()
                )
            )
        }
        existingIds.filterNot { it in remoteIds }.forEach { videoId ->
            playlistDao.removeVideoFromPlaylist(playlistId, videoId)
        }
        val newThumb = playlistDao.getFirstVideoThumbnail(playlistId) ?: ""
        playlistDao.updatePlaylistThumbnail(playlistId, newThumb, pid())
    }

    suspend fun getPlaylistInfo(playlistId: String): PlaylistInfo? {
        val entity = playlistDao.getPlaylist(playlistId, pid()) ?: return null
        return PlaylistInfo(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            videoCount = 0,
            thumbnailUrl = entity.thumbnailUrl,
            isPrivate = entity.isPrivate,
            createdAt = entity.createdAt
        )
    }
}
