package com.omersusin.pitube.data

sealed interface LiveChatRun {
    data class Text(val text: String) : LiveChatRun
    data class Emoji(val label: String, val imageUrl: String?) : LiveChatRun
}

enum class LiveChatBadgeKind { OWNER, MODERATOR, VERIFIED, MEMBER }

data class LiveChatBadge(
    val kind: LiveChatBadgeKind,
    val tooltip: String,
    val imageUrl: String? = null,
)

data class LiveChatAuthor(
    val name: String,
    val channelId: String? = null,
    val photoUrl: String? = null,
    val badges: List<LiveChatBadge> = emptyList(),
) {
    val isOwner: Boolean get() = badges.any { it.kind == LiveChatBadgeKind.OWNER }
    val isModerator: Boolean get() = badges.any { it.kind == LiveChatBadgeKind.MODERATOR }
    val isMember: Boolean get() = badges.any { it.kind == LiveChatBadgeKind.MEMBER }
}

sealed interface LiveChatMessage {
    val id: String
    val timestampUsec: Long
    val author: LiveChatAuthor?

    data class Text(
        override val id: String,
        override val timestampUsec: Long,
        override val author: LiveChatAuthor,
        val runs: List<LiveChatRun>,
    ) : LiveChatMessage

    data class Paid(
        override val id: String,
        override val timestampUsec: Long,
        override val author: LiveChatAuthor,
        val runs: List<LiveChatRun>,
        val amountText: String,
        val headerBackgroundColor: Long,
        val headerTextColor: Long,
        val bodyBackgroundColor: Long,
        val bodyTextColor: Long,
        val stickerUrl: String? = null,
    ) : LiveChatMessage

    data class Membership(
        override val id: String,
        override val timestampUsec: Long,
        override val author: LiveChatAuthor,
        val headline: String,
        val tierName: String?,
    ) : LiveChatMessage

    data class Gift(
        override val id: String,
        override val timestampUsec: Long,
        override val author: LiveChatAuthor,
        val text: String,
        val giftImageUrl: String?,
    ) : LiveChatMessage

    data class System(
        override val id: String,
        override val timestampUsec: Long,
        val runs: List<LiveChatRun>,
    ) : LiveChatMessage {
        override val author: LiveChatAuthor? get() = null
    }
}

data class LiveChatBanner(
    val id: String,
    val author: LiveChatAuthor?,
    val runs: List<LiveChatRun>,
    val isSummary: Boolean,
)

data class LiveChatSession(
    val continuation: String,
)

data class LiveChatPage(
    val messages: List<LiveChatMessage> = emptyList(),
    val removedIds: Set<String> = emptySet(),
    val removedAuthorIds: Set<String> = emptySet(),
    val replacements: Map<String, LiveChatMessage> = emptyMap(),
    val banner: LiveChatBanner? = null,
    val bannerCleared: Boolean = false,
    val restrictionMessage: String? = null,
    val nextContinuation: String? = null,
    val timeoutMs: Long = 10_000L,
    val sendParams: String? = null,
    val maxMessageLength: Int = 200,
)

data class LiveChatSendResult(
    val success: Boolean,
    val echo: LiveChatMessage? = null,
    val error: String? = null,
)

data class LiveMetadata(
    val viewerCountText: String? = null,
    val shortViewerCount: String? = null,
    val dateText: String? = null,
)

data class ChatMessage(val author: String, val text: String, val timestamp: String)
