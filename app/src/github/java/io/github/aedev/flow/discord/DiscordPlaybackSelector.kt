package io.github.aedev.flow.discord

class DiscordPlaybackSelector {
    fun select(
        short: PlaybackSnapshot?,
        video: PlaybackSnapshot?,
    ): PlaybackSnapshot? = sequenceOf(short, video)
        .filterNotNull()
        .firstOrNull { snapshot -> snapshot.isPlaying && snapshot.mediaId.isNotBlank() }
}
