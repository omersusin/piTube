package com.omersusin.pitube.data.local.dao

import androidx.room.*
import com.omersusin.pitube.data.local.entity.PlaylistEntity
import com.omersusin.pitube.data.local.entity.PlaylistVideoCrossRef
import com.omersusin.pitube.data.local.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    @ColumnInfo(name = "video_count") val videoCount: Int
)

/** A playlist's video joined with per-playlist metadata (when it was added to that playlist). */
data class PlaylistVideoWithMeta(
    @Embedded val video: VideoEntity,
    @ColumnInfo(name = "addedAt") val addedAt: Long
)

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("SELECT playlists.*, COUNT(playlist_video_cross_ref.videoId) as video_count FROM playlists LEFT JOIN playlist_video_cross_ref ON playlists.id = playlist_video_cross_ref.playlistId WHERE playlists.profileId = :profileId GROUP BY playlists.id ORDER BY createdAt DESC")
    fun getAllPlaylistsWithCount(profileId: String): Flow<List<PlaylistWithCount>>

    @Query("SELECT playlists.*, COUNT(playlist_video_cross_ref.videoId) as video_count FROM playlists LEFT JOIN playlist_video_cross_ref ON playlists.id = playlist_video_cross_ref.playlistId WHERE playlists.profileId = :profileId AND isMusic = 1 GROUP BY playlists.id ORDER BY createdAt DESC")
    fun getMusicPlaylistsWithCount(profileId: String): Flow<List<PlaylistWithCount>>

    @Query("SELECT playlists.*, COUNT(playlist_video_cross_ref.videoId) as video_count FROM playlists LEFT JOIN playlist_video_cross_ref ON playlists.id = playlist_video_cross_ref.playlistId WHERE playlists.profileId = :profileId AND isMusic = 0 AND playlists.id NOT IN ('watch_later', 'saved_shorts') AND playlists.id NOT LIKE 'watch\\_later@%' ESCAPE '\\' AND playlists.id NOT LIKE 'saved\\_shorts@%' ESCAPE '\\' GROUP BY playlists.id ORDER BY createdAt DESC")
    fun getVideoPlaylistsWithCount(profileId: String): Flow<List<PlaylistWithCount>>

    @Query("SELECT playlists.*, COUNT(playlist_video_cross_ref.videoId) as video_count FROM playlists LEFT JOIN playlist_video_cross_ref ON playlists.id = playlist_video_cross_ref.playlistId WHERE playlists.profileId = :profileId AND isMusic = 0 AND isUserCreated = 1 AND playlists.id NOT IN ('watch_later', 'saved_shorts') AND playlists.id NOT LIKE 'watch\\_later@%' ESCAPE '\\' AND playlists.id NOT LIKE 'saved\\_shorts@%' ESCAPE '\\' GROUP BY playlists.id ORDER BY createdAt DESC")
    fun getUserCreatedVideoPlaylistsWithCount(profileId: String): Flow<List<PlaylistWithCount>>

    @Query("SELECT playlists.*, COUNT(playlist_video_cross_ref.videoId) as video_count FROM playlists LEFT JOIN playlist_video_cross_ref ON playlists.id = playlist_video_cross_ref.playlistId WHERE playlists.profileId = :profileId AND isMusic = 0 AND isUserCreated = 0 AND playlists.id NOT IN ('watch_later', 'saved_shorts') AND playlists.id NOT LIKE 'watch\\_later@%' ESCAPE '\\' AND playlists.id NOT LIKE 'saved\\_shorts@%' ESCAPE '\\' GROUP BY playlists.id ORDER BY createdAt DESC")
    fun getSavedVideoPlaylistsWithCount(profileId: String): Flow<List<PlaylistWithCount>>

    @Query("SELECT playlists.*, COUNT(playlist_video_cross_ref.videoId) as video_count FROM playlists LEFT JOIN playlist_video_cross_ref ON playlists.id = playlist_video_cross_ref.playlistId WHERE playlists.profileId = :profileId AND isMusic = 1 AND isUserCreated = 1 GROUP BY playlists.id ORDER BY createdAt DESC")
    fun getUserCreatedMusicPlaylistsWithCount(profileId: String): Flow<List<PlaylistWithCount>>

    @Query("SELECT playlists.*, COUNT(playlist_video_cross_ref.videoId) as video_count FROM playlists LEFT JOIN playlist_video_cross_ref ON playlists.id = playlist_video_cross_ref.playlistId WHERE playlists.profileId = :profileId AND isMusic = 1 AND isUserCreated = 0 GROUP BY playlists.id ORDER BY createdAt DESC")
    fun getSavedMusicPlaylistsWithCount(profileId: String): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun getAllPlaylists(profileId: String): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE profileId = :profileId AND isMusic = 1 ORDER BY createdAt DESC")
    fun getMusicPlaylists(profileId: String): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE profileId = :profileId AND isMusic = 0 ORDER BY createdAt DESC")
    fun getVideoPlaylists(profileId: String): Flow<List<PlaylistEntity>>

    /** Unscoped lookup — only for legacy adoption / migration paths. */
    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistAnyProfile(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :id AND profileId = :profileId LIMIT 1")
    suspend fun getPlaylist(id: String, profileId: String): PlaylistEntity?

    @Query("DELETE FROM playlists WHERE id = :id AND profileId = :profileId")
    suspend fun deletePlaylist(id: String, profileId: String)

    @Query("UPDATE playlists SET name = :name WHERE id = :id AND profileId = :profileId")
    suspend fun updatePlaylistName(id: String, name: String, profileId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistVideoCrossRef(crossRef: PlaylistVideoCrossRef)

    @Query("UPDATE playlist_video_cross_ref SET position = :position WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun updatePlaylistVideoPosition(playlistId: String, videoId: String, position: Long)

    @Query("DELETE FROM playlist_video_cross_ref WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun removeVideoFromPlaylist(playlistId: String, videoId: String)

    @Transaction
    @Query("SELECT videos.* FROM videos INNER JOIN playlist_video_cross_ref ON videos.id = playlist_video_cross_ref.videoId WHERE playlist_video_cross_ref.playlistId = :playlistId ORDER BY playlist_video_cross_ref.position ASC")
    fun getVideosForPlaylist(playlistId: String): Flow<List<VideoEntity>>

    @Transaction
    @Query("SELECT videos.*, playlist_video_cross_ref.addedAt AS addedAt FROM videos INNER JOIN playlist_video_cross_ref ON videos.id = playlist_video_cross_ref.videoId WHERE playlist_video_cross_ref.playlistId = :playlistId ORDER BY playlist_video_cross_ref.position ASC")
    fun getVideosWithMetaForPlaylist(playlistId: String): Flow<List<PlaylistVideoWithMeta>>

    @Query("SELECT COUNT(*) FROM playlist_video_cross_ref WHERE playlistId = :playlistId")
    fun getPlaylistVideoCount(playlistId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM playlist_video_cross_ref WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun isVideoInPlaylist(playlistId: String, videoId: String): Int

    @Query("""
        SELECT COUNT(*) FROM playlist_video_cross_ref r
        INNER JOIN playlists p ON p.id = r.playlistId
        WHERE r.videoId = :videoId AND p.isMusic = 0 AND p.profileId = :profileId
    """)
    fun getVideoPlaylistMembershipCount(videoId: String, profileId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM playlists WHERE id = :id AND profileId = :profileId AND isUserCreated = 0")
    suspend fun isSavedExternalPlaylist(id: String, profileId: String): Int

    @Query("SELECT COUNT(*) FROM playlists WHERE id = :id AND profileId = :profileId")
    suspend fun isPlaylistInDb(id: String, profileId: String): Int

    @Query("SELECT * FROM playlist_video_cross_ref")
    suspend fun getAllPlaylistVideoCrossRefs(): List<PlaylistVideoCrossRef>

    /** Distinct videos saved across Watch Later and all non-music video playlists (excludes saved shorts). */
    @Query("""
        SELECT v.* FROM videos v
        INNER JOIN playlist_video_cross_ref r ON v.id = r.videoId
        INNER JOIN playlists p ON p.id = r.playlistId
        WHERE p.profileId = :profileId AND p.isMusic = 0 AND p.id != 'saved_shorts'
        AND p.id NOT LIKE 'saved\\_shorts@%' ESCAPE '\\'
        GROUP BY v.id
        ORDER BY MIN(r.position) ASC
    """)
    suspend fun getSavedVideoPlaylistVideos(profileId: String): List<VideoEntity>

    @Query("UPDATE playlists SET thumbnailUrl = :thumbnailUrl WHERE id = :id AND profileId = :profileId")
    suspend fun updatePlaylistThumbnail(id: String, thumbnailUrl: String, profileId: String)

    @Query("SELECT v.thumbnailUrl FROM videos v INNER JOIN playlist_video_cross_ref r ON v.id = r.videoId WHERE r.playlistId = :playlistId ORDER BY r.position ASC LIMIT 1")
    suspend fun getFirstVideoThumbnail(playlistId: String): String?

    /**
     * Returns stub VideoEntities inside music playlists that are missing a title OR a thumbnail.
     * Used for background enrichment — e.g. synced album tracks arrive with a title but no artwork.
     */
    @Query("""
        SELECT DISTINCT v.* FROM videos v
        INNER JOIN playlist_video_cross_ref r ON v.id = r.videoId
        INNER JOIN playlists p ON p.id = r.playlistId
        WHERE p.profileId = :profileId AND p.isMusic = 1 AND (v.title = '' OR v.title IS NULL OR v.thumbnailUrl = '' OR v.thumbnailUrl IS NULL)
        LIMIT 200
    """)
    suspend fun getMusicPlaylistStubVideos(profileId: String): List<VideoEntity>

    /** Music playlist/album ids whose cover is blank — recompute from the first track after enrichment. */
    @Query("SELECT id FROM playlists WHERE profileId = :profileId AND isMusic = 1 AND (thumbnailUrl = '' OR thumbnailUrl IS NULL)")
    suspend fun getMusicPlaylistsMissingThumbnail(profileId: String): List<String>

    // ── Legacy adoption (pre-scope rows with profileId = '') ────────────────

    @Query("UPDATE playlists SET profileId = :targetProfileId WHERE profileId = ''")
    suspend fun adoptLegacyRows(targetProfileId: String)

    /** Rename a system playlist id (e.g. watch_later → watch_later@pid) including cross-refs. */
    @Transaction
    suspend fun renamePlaylistId(oldId: String, newId: String, profileId: String) {
        dbUpdatePlaylistId(oldId, newId, profileId)
        dbMoveCrossRefs(oldId, newId)
    }

    @Query("UPDATE playlists SET id = :newId WHERE id = :oldId AND profileId = :profileId")
    suspend fun dbUpdatePlaylistId(oldId: String, newId: String, profileId: String)

    @Query("UPDATE playlist_video_cross_ref SET playlistId = :newId WHERE playlistId = :oldId")
    suspend fun dbMoveCrossRefs(oldId: String, newId: String)

    // ── Unscoped variants (device-level backup/sync only) ───────────────────

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun getAllPlaylistsUnscoped(): List<PlaylistEntity>

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistAnyProfile(id: String)
}
