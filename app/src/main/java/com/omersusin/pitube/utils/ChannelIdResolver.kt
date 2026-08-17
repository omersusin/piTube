package com.omersusin.pitube.utils

/**
 * Canonical `UC...` channel-id resolution for subscription state and writes.
 *
 * YouTube subscription rows and the subscription/subscribe endpoint only
 * accept canonical `UC...` ids, but stream metadata frequently only carries
 * the uploader URL (`/@handle`, `/user/name`, `/c/name`, `/channel/UC...`).
 * Keying the local store with the bare URL tail made every row written from
 * the player invisible to the channel page (which always has the canonical
 * id), so followed channels showed "Subscribe".
 */
object ChannelIdResolver {

    fun isCanonical(id: String?): Boolean =
        id != null && id.startsWith("UC") && id.length > 10 &&
            !id.contains("/") && !id.contains("@")

    /**
     * Prefer [channelId] when it is already canonical; otherwise derive the
     * id from [uploaderUrl]. Non-canonical handles are returned as-is so
     * callers can still key local state — the remote write itself is gated
     * in YouTube.setSubscribed.
     */
    fun resolve(channelId: String?, uploaderUrl: String?): String {
        if (isCanonical(channelId)) return channelId!!
        val url = uploaderUrl.orEmpty()
        if (url.isBlank()) return channelId.orEmpty()
        val fromUrl = if (url.contains("/channel/")) {
            url.substringAfterLast("/channel/")
                .substringBefore("/")
                .substringBefore("?")
                .substringBefore("&")
        } else {
            url.trimEnd('/').substringAfterLast("/").substringBefore("?")
        }
        return fromUrl.ifBlank { channelId.orEmpty() }
    }
}
