package com.omersusin.pitube.sync.apply

import android.content.Context
import com.omersusin.pitube.data.local.LikedVideosRepository
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.local.SubscriptionRepository
import com.omersusin.pitube.data.local.dao.PlaylistDao
import com.omersusin.pitube.data.local.dao.VideoDao
import com.omersusin.pitube.data.local.dao.WatchHistoryDao
import com.omersusin.pitube.sync.canonical.CanonicalLike
import com.omersusin.pitube.sync.canonical.CanonicalPlaylist
import com.omersusin.pitube.sync.canonical.CanonicalSetting
import com.omersusin.pitube.sync.canonical.CanonicalSubscriptionGroup
import com.omersusin.pitube.sync.canonical.CanonicalWatchHistory
import com.omersusin.pitube.sync.mapping.LikesMapper
import com.omersusin.pitube.sync.mapping.PlaylistMapper
import com.omersusin.pitube.sync.mapping.SettingsMapper
import com.omersusin.pitube.sync.mapping.WatchHistoryMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The bridge between platform-neutral canonical records and the app's real stores (Room DAOs,
 * DataStore singletons). Provides `read*` (local → canonical, for the send side) and `write*`
 * (merged canonical → store, for the apply side).
 */
@Singleton
class SyncDataAccess @Inject constructor(
    @ApplicationContext private val context: Context,
    private val watchHistoryDao: WatchHistoryDao,
    private val playlistDao: PlaylistDao,
    private val videoDao: VideoDao,
) {
    private val likedVideos: LikedVideosRepository by lazy { LikedVideosRepository.getInstance(context) }
    private val playerPrefs: PlayerPreferences by lazy { PlayerPreferences(context) }
    private val subscriptionRepository: SubscriptionRepository by lazy { SubscriptionRepository.getInstance(context) }

    // --- watch history ---

    // Sync is a device-level backup: it spans every profile. Legacy '' rows and
    // per-profile rows are both included; entities are written with their
    // existing profileId (blank = legacy, adopted at startup).
    suspend fun readWatchHistory(node: String): List<CanonicalWatchHistory> =
        watchHistoryDao.getAllHistoryUnscoped()
            .filter { !it.isLocal } // device-local media files don't sync
            .map { WatchHistoryMapper.toCanonical(it, node) }

    suspend fun writeWatchHistory(merged: List<CanonicalWatchHistory>) {
        val toUpsert = merged.filter { !it.deleted }.map { WatchHistoryMapper.toEntity(it) }
        if (toUpsert.isNotEmpty()) watchHistoryDao.upsertAll(toUpsert)
        for (d in merged) if (d.deleted) watchHistoryDao.deleteEntryUnscoped(d.videoId)
    }

    // --- likes (export is liked-only; apply handles all 3 states) ---

    suspend fun readLikes(node: String): List<CanonicalLike> =
        likedVideos.getAllLikedVideos().first().map { LikesMapper.likedToCanonical(it, node) }

    suspend fun writeLikes(merged: List<CanonicalLike>) {
        for (like in merged) when (like.state) {
            CanonicalLike.STATE_LIKED -> likedVideos.likeVideo(LikesMapper.toLikedInfo(like))
            CanonicalLike.STATE_DISLIKED -> likedVideos.dislikeVideo(like.id)
            CanonicalLike.STATE_NONE -> likedVideos.removeLikeState(like.id)
        }
    }

    // --- settings (curated whitelist) ---

    suspend fun readSettings(hlc: String): List<CanonicalSetting> =
        SettingsMapper.exportToCanonical(playerPrefs.getExportData(), hlc)

    suspend fun writeSettings(merged: List<CanonicalSetting>) {
        playerPrefs.restoreData(SettingsMapper.applyToBackup(merged))
    }

    // --- subscriptions ---

    // The real subscription list lives in SubscriptionRepository (per-profile
    // DataStore); the vestigial subscription_groups Room table is never written
    // by the UI. Export one synthetic "Subscriptions" group so device-to-device
    // sync carries the actual channels instead of an always-empty table.
    suspend fun readSubscriptions(hlc: String): List<CanonicalSubscriptionGroup> {
        val channelIds = subscriptionRepository.getValidSubscriptionIds()
        if (channelIds.isEmpty()) return emptyList()
        return listOf(
            CanonicalSubscriptionGroup(
                name = "Subscriptions",
                channelIds = channelIds.toSortedSet().toList(),
                sortOrder = 0,
                hlc = hlc,
            )
        )
    }

    suspend fun writeSubscriptions(merged: List<CanonicalSubscriptionGroup>) {
        val existing = subscriptionRepository.getValidSubscriptionIds()
        for (group in merged) {
            if (group.deleted) {
                for (id in group.channelIds) subscriptionRepository.unsubscribe(id)
                continue
            }
            for (id in group.channelIds) {
                if (id.isBlank() || id in existing) continue
                subscriptionRepository.subscribe(
                    com.omersusin.pitube.data.local.ChannelSubscription(
                        channelId = id,
                        channelName = "",
                        channelThumbnail = "",
                    )
                )
            }
        }
    }

    // --- playlists ---

    suspend fun readPlaylists(hlc: String): List<CanonicalPlaylist> {
        val playlists = playlistDao.getAllPlaylistsUnscoped()
        val refsByPlaylist = playlistDao.getAllPlaylistVideoCrossRefs().groupBy { it.playlistId }
        val videosById = videoDao.getAllVideos().associateBy { it.id }
        return playlists.map { p ->
            val items = (refsByPlaylist[p.id] ?: emptyList()).map { ref ->
                PlaylistMapper.ItemSource(ref, videosById[ref.videoId])
            }
            PlaylistMapper.toCanonical(p, items, hlc)
        }
    }

    suspend fun writePlaylists(merged: List<CanonicalPlaylist>) {
        val locals = playlistDao.getAllPlaylistsUnscoped()
        val bySyncId = locals.associateBy { it.syncId ?: it.id }
        val byYoutubeId = locals.filter { !it.isUserCreated }.associateBy { it.id }
        val allRefs = playlistDao.getAllPlaylistVideoCrossRefs().groupBy { it.playlistId }

        for (cp in merged) {
            val localId = resolveLocalId(cp, bySyncId, byYoutubeId)
            if (cp.deleted) {
                if (localId != null && localId != PlaylistMapper.WATCH_LATER_ID &&
                    localId != PlaylistMapper.SAVED_SHORTS_ID
                ) {
                    playlistDao.deletePlaylistAnyProfile(localId)
                }
                continue
            }
            val targetId = localId ?: newLocalId(cp)
            playlistDao.insertPlaylist(PlaylistMapper.toPlaylistEntity(cp, targetId))
            videoDao.insertVideosOrIgnore(PlaylistMapper.toVideoEntities(cp))

            val mergedRefs = PlaylistMapper.toCrossRefs(cp, targetId)
            val mergedVids = mergedRefs.map { it.videoId }.toSet()
            // Remove refs no longer present, then upsert the merged set (positions updated).
            for (ref in allRefs[targetId].orEmpty()) {
                if (ref.videoId !in mergedVids) playlistDao.removeVideoFromPlaylist(targetId, ref.videoId)
            }
            for (ref in mergedRefs) playlistDao.insertPlaylistVideoCrossRef(ref)
        }
    }

    private fun resolveLocalId(
        cp: CanonicalPlaylist,
        bySyncId: Map<String, com.omersusin.pitube.data.local.entity.PlaylistEntity>,
        byYoutubeId: Map<String, com.omersusin.pitube.data.local.entity.PlaylistEntity>,
    ): String? {
        if (cp.syncId == CanonicalPlaylist.RESERVED_WATCH_LATER) return PlaylistMapper.WATCH_LATER_ID
        bySyncId[cp.syncId]?.let { return it.id }
        if (cp.origin == CanonicalPlaylist.ORIGIN_YOUTUBE && cp.youtubeId != null) {
            byYoutubeId[cp.youtubeId]?.let { return it.id }
        }
        return null
    }

    private fun newLocalId(cp: CanonicalPlaylist): String = when {
        cp.syncId == CanonicalPlaylist.RESERVED_WATCH_LATER -> PlaylistMapper.WATCH_LATER_ID
        cp.origin == CanonicalPlaylist.ORIGIN_YOUTUBE && cp.youtubeId != null -> cp.youtubeId
        else -> "sync_${UUID.randomUUID()}"
    }
}
