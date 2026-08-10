package com.omersusin.pitube.sync.canonical

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Platform-neutral canonical records exchanged over FLOW-SYNC/1.
 *
 * Conventions: epoch **milliseconds** for all times; `progress` is a 0..1 fraction;
 * `durationSeconds` is integer seconds; deletions are **tombstones** (`deleted=true`), never
 * omissions; every mergeable record carries an `hlc` string. Android maps its
 * Room/DataStore values to/from these in `sync/mapping`.
 *
 * These types are the unit the merge engine operates on, so they are deliberately decoupled
 * from both DB schemas. Keep field names in sync with the desktop `canonical.rs`.
 */

@Serializable
data class CanonicalWatchHistory(
    val videoId: String,
    val title: String = "",
    val channelName: String = "",
    val channelId: String = "",
    val thumbnailUrl: String = "",
    val watchedAtMs: Long = 0,
    val progress: Double = 0.0,
    val durationSeconds: Long = 0,
    val isMusic: Boolean = false,
    val isShort: Boolean = false,
    val hlc: String = "",
    val deleted: Boolean = false,
)

@Serializable
data class CanonicalPlaylistItem(
    val videoId: String,
    /** Ascending display rank (0-based).*/
    val position: Long = 0,
    val addedAtMs: Long = 0,
    val deleted: Boolean = false,
    val title: String = "",
    val channelName: String = "",
    val channelId: String = "",
    val thumbnailUrl: String = "",
    val durationSeconds: Long = 0,
    val isMusic: Boolean = false,
    val hlc: String = "",
)

@Serializable
data class CanonicalPlaylist(
    val syncId: String,
    val origin: String = ORIGIN_LOCAL,
    val youtubeId: String? = null,
    val title: String = "",
    val description: String = "",
    val isMusic: Boolean = false,
    val isUserCreated: Boolean = true,
    val isProtected: Boolean = false,
    val createdAtMs: Long = 0,
    val updatedHlc: String = "",
    val deleted: Boolean = false,
    val items: List<CanonicalPlaylistItem> = emptyList(),
) {
    companion object {
        const val ORIGIN_LOCAL = "local"
        const val ORIGIN_YOUTUBE = "youtube"
        /** Reserved id for the cross-platform Watch Later playlist */
        const val RESERVED_WATCH_LATER = "reserved:watch-later"
    }
}

/** Minimal display metadata for a like (desktop §6.3 `meta`). `artist` ⇄ Android `channelName`. */
@Serializable
data class CanonicalLikeMeta(
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String = "",
)

@Serializable
data class CanonicalLike(
    val kind: String, // "video" | "music"
    val id: String,
    val state: String, // "liked" | "disliked" | "none"
    val updatedAtMs: Long = 0,
    val hlc: String = "",
    val meta: CanonicalLikeMeta = CanonicalLikeMeta(),
    val title: String = "",
    val channelName: String = "",
    val thumbnailUrl: String = "",
) {
    companion object {
        const val KIND_VIDEO = "video"
        const val KIND_MUSIC = "music"
        const val STATE_LIKED = "liked"
        const val STATE_DISLIKED = "disliked"
        const val STATE_NONE = "none"
    }
}

/** A single synced setting. [value] is a typed JSON primitive; the mapper coerces per key. */
@Serializable
data class CanonicalSetting(
    val key: String,
    val value: JsonElement,
    val hlc: String = "",
)

@Serializable
data class CanonicalSubscriptionGroup(
    val name: String,
    val channelIds: List<String> = emptyList(),
    val sortOrder: Int = 0,
    val hlc: String = "",
    val deleted: Boolean = false,
)
