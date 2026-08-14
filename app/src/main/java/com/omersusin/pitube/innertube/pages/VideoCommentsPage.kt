package com.omersusin.pitube.innertube.pages

import android.util.Base64
import com.omersusin.pitube.data.model.Comment
import java.net.URLDecoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

data class VideoCommentsPage(
    val comments: List<Comment>,
    val continuation: String?,
    val createCommentParams: String?,
)

/**
 * Initial comments entry token from a watch-page `next` response.
 * The itemSectionRenderer tagged `comment-item-section` usually carries two
 * tokens; the longest is the full comments panel.
 */
internal fun JsonElement.toVideoCommentsToken(): String? {
    val sections = mutableListOf<JsonObject>()
    findObjectsByKey(this, "itemSectionRenderer", sections)
    var best: String? = null
    for (section in sections) {
        if (section["sectionIdentifier"].stringOrNull() != "comment-item-section") continue
        val tokens = mutableListOf<String>()
        findContinuationTokens(section, tokens)
        val longest = tokens.maxByOrNull { it.length }
        if (longest != null && longest.length > (best?.length ?: 0)) best = longest
    }
    return best
}

/**
 * Parse one page of comments (top-level or replies) from a continuation token.
 * Port of Koda's getCommentsPage: entity payloads carry the comment data,
 * toolbar surface entities carry the like/unlike/reply/delete action params.
 */
internal fun JsonElement.toVideoCommentsPage(): VideoCommentsPage {
    val root = this as? JsonObject
        ?: return VideoCommentsPage(comments = emptyList(), continuation = null, createCommentParams = null)
    val entities = mutableMapOf<String, JsonObject>()
    val toolbarStates = mutableMapOf<String, JsonObject>()
    val toolbarSurfaces = mutableMapOf<String, JsonObject>()
    val replyParamsList = mutableListOf<String>()

    val mutations = root["frameworkUpdates"].objectOrNull()
        ?.get("entityBatchUpdate").objectOrNull()
        ?.get("mutations").arrayOrNull()
    if (mutations != null) {
        for (mutation in mutations) {
            val payload = mutation.objectOrNull()?.get("payload").objectOrNull() ?: continue
            payload["commentEntityPayload"].objectOrNull()?.let { entity ->
                val id = entity["properties"].objectOrNull()
                    ?.get("commentId").stringOrNull()
                if (!id.isNullOrBlank()) entities[id] = entity
            }
            payload["engagementToolbarStateEntityPayload"].objectOrNull()?.let { state ->
                val key = state["key"].stringOrNull()
                if (!key.isNullOrBlank()) toolbarStates[key] = state
            }
            payload["engagementToolbarSurfaceEntityPayload"].objectOrNull()?.let { surface ->
                val key = surface["key"].stringOrNull()
                if (!key.isNullOrBlank()) toolbarSurfaces[key] = surface
                val replyEndpoints = mutableListOf<JsonObject>()
                findObjectsByKey(surface, "createCommentReplyEndpoint", replyEndpoints)
                replyEndpoints.firstOrNull()?.get("createReplyParams").stringOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { replyParamsList.add(it) }
            }
        }
    }

    // Match reply params to their comment: the decoded protobuf embeds the commentId
    val replyParamsByCommentId = mutableMapOf<String, String>()
    for (params in replyParamsList) {
        val decoded = decodeInnerTubeParams(params) ?: continue
        entities.keys.firstOrNull { decoded.contains(it) }?.let { id ->
            replyParamsByCommentId[id] = params
        }
    }

    // Params for posting a new top-level comment (present on first pages only)
    val createEndpoints = mutableListOf<JsonObject>()
    findObjectsByKey(this, "createCommentEndpoint", createEndpoints)
    val createCommentParams = createEndpoints.firstOrNull()
        ?.get("createCommentParams").stringOrNull()
        ?.takeIf { it.isNotBlank() }

    // Walk continuationItems in order to keep YouTube's comment ordering
    val comments = mutableListOf<Comment>()
    var nextToken: String? = null
    val endpoints = root["onResponseReceivedEndpoints"].arrayOrNull() ?: JsonArray(emptyList())
    for (endpoint in endpoints) {
        val endpointObject = endpoint.objectOrNull() ?: continue
        val items = (endpointObject["reloadContinuationItemsCommand"].objectOrNull()
            ?: endpointObject["appendContinuationItemsAction"].objectOrNull())
            ?.get("continuationItems").arrayOrNull()
            ?: continue
        for (item in items) {
            val itemObject = item.objectOrNull() ?: continue
            val thread = itemObject["commentThreadRenderer"].objectOrNull()
            // Top-level pages wrap comments in commentThreadRenderer;
            // reply pages carry bare commentViewModel items.
            val rawViewModel = (thread ?: itemObject)["commentViewModel"].objectOrNull()
            val viewModel = rawViewModel?.let { vm -> vm["commentViewModel"].objectOrNull() ?: vm }
            if (viewModel != null) {
                val id = viewModel["commentId"].stringOrNull()
                if (id == null) continue
                val entity = entities[id] ?: continue
                var repliesToken: String? = null
                thread?.get("replies").objectOrNull()?.let { replies ->
                    val tokens = mutableListOf<String>()
                    findContinuationTokens(replies, tokens)
                    repliesToken = tokens.firstOrNull()
                }
                parseVideoCommentEntity(
                    entity = entity,
                    viewModel = viewModel,
                    toolbarStates = toolbarStates,
                    repliesToken = repliesToken,
                    replyParams = replyParamsByCommentId[id],
                    toolbarSurfaces = toolbarSurfaces,
                )?.let(comments::add)
            } else if (itemObject.containsKey("continuationItemRenderer")) {
                val tokens = mutableListOf<String>()
                findContinuationTokens(itemObject["continuationItemRenderer"], tokens)
                if (nextToken == null) nextToken = tokens.firstOrNull()
            }
        }
    }

    return VideoCommentsPage(
        comments = comments.distinctBy(Comment::id),
        continuation = nextToken,
        createCommentParams = createCommentParams,
    )
}

/**
 * Parse the comment entity out of a create_comment / create_comment_reply
 * response. Returns null unless the actionResult reports success.
 */
internal fun JsonElement.toCreatedVideoComment(): Comment? {    val actionResults = mutableListOf<JsonObject>()
    findObjectsByKey(this, "actionResult", actionResults)
    if (actionResults.none { it["status"].stringOrNull() == "STATUS_SUCCEEDED" }) return null

    val entities = mutableListOf<JsonObject>()
    findObjectsByKey(this, "commentEntityPayload", entities)
    val entity = entities.firstOrNull() ?: return null
    val id = entity["properties"].objectOrNull()
        ?.get("commentId").stringOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: return null

    val replyEndpoints = mutableListOf<JsonObject>()
    findObjectsByKey(this, "createCommentReplyEndpoint", replyEndpoints)
    val replyParams = replyEndpoints.firstOrNull()
        ?.get("createReplyParams").stringOrNull()
        ?.takeIf { it.isNotBlank() }

    // The create response also carries the comment's viewModel and its
    // toolbar surface — passing them through gives the fresh comment its
    // like/delete params immediately (no page reload needed)
    val viewModels = mutableListOf<JsonObject>()
    findObjectsByKey(this, "commentViewModel", viewModels)
    val viewModel = viewModels
        .map { it["commentViewModel"].objectOrNull() ?: it }
        .firstOrNull { it["commentId"].stringOrNull() == id }
        ?: JsonObject(emptyMap())

    val surfaces = mutableListOf<JsonObject>()
    findObjectsByKey(this, "engagementToolbarSurfaceEntityPayload", surfaces)
    val toolbarSurfaces = surfaces
        .filter { !it["key"].stringOrNull().isNullOrBlank() }
        .associateBy { it["key"].stringOrNull().orEmpty() }

    return parseVideoCommentEntity(
        entity = entity,
        viewModel = viewModel,
        toolbarStates = emptyMap(),
        repliesToken = null,
        replyParams = replyParams,
        toolbarSurfaces = toolbarSurfaces,
    )
}

private fun parseVideoCommentEntity(
    entity: JsonObject,
    viewModel: JsonObject,
    toolbarStates: Map<String, JsonObject>,
    repliesToken: String?,
    replyParams: String? = null,
    toolbarSurfaces: Map<String, JsonObject> = emptyMap(),
): Comment? {    val properties = entity["properties"].objectOrNull()
    val author = entity["author"].objectOrNull()
    val toolbar = entity["toolbar"].objectOrNull()
    val id = properties?.get("commentId").stringOrNull()
        ?: viewModel["commentId"].stringOrNull()
        ?: return null
    val toolbarStateKey = properties?.get("toolbarStateKey").stringOrNull().orEmpty()
    val toolbarState = toolbarStates[toolbarStateKey]
    val heartState = toolbarState?.get("heartState").stringOrNull()
    val likeState = toolbarState?.get("likeState").stringOrNull()

    // Like/unlike actions come from the comment's toolbar surface entity
    // (signed-in responses only; signed out the commands are empty stubs)
    val surface = toolbarSurfaces[viewModel["toolbarSurfaceKey"].stringOrNull().orEmpty()]
    fun surfaceAction(command: String): String? =
        surface?.get(command).objectOrNull()?.let {
            val endpoints = mutableListOf<JsonObject>()
            findObjectsByKey(it, "performCommentActionEndpoint", endpoints)
            endpoints.firstOrNull()?.get("action").stringOrNull()
                ?.takeIf { action -> action.isNotBlank() }
        }

    // Own comments carry a "Delete" item in the surface's three-dot menu
    // (menuCommand -> menuRenderer -> menuNavigationItemRenderer, label is
    // stable because the webContext pins hl=en)
    val deleteParams = surface?.get("menuCommand").objectOrNull()?.let { menu ->
        val menuItems = mutableListOf<JsonObject>()
        findObjectsByKey(menu, "menuNavigationItemRenderer", menuItems)
        menuItems.firstOrNull { it["text"].youtubeText() == "Delete" }?.let { item ->
            val endpoints = mutableListOf<JsonObject>()
            findObjectsByKey(item, "performCommentActionEndpoint", endpoints)
            endpoints.firstOrNull()?.get("action").stringOrNull()
                ?.takeIf { action -> action.isNotBlank() }
        }
    }

    val avatar = entity["avatar"].objectOrNull()
        ?.get("image").objectOrNull()
        ?.get("sources").largestThumbnailUrl()
        ?: author?.get("avatar").objectOrNull()
            ?.get("image").objectOrNull()
            ?.get("sources").largestThumbnailUrl()
        ?: ""

    return Comment(
        id = id,
        author = author?.get("displayName").stringOrNull().orEmpty(),
        authorThumbnail = normalizeCommentImageUrl(avatar),
        text = properties?.get("content").youtubeText().orEmpty(),
        likeCount = parseCount(toolbar?.get("likeCountNotliked")),
        publishedTime = properties?.get("publishedTime").stringOrNull().orEmpty(),
        replyCount = parseCount(toolbar?.get("replyCount")),
        isPinned = viewModel.containsKey("pinnedText"),
        continuationToken = repliesToken,
        authorChannelId = author?.get("channelId").stringOrNull()
            ?: author?.get("navigationEndpoint").objectOrNull()
                ?.get("browseEndpoint").objectOrNull()
                ?.get("browseId").stringOrNull()
            ?: "",
        isHearted = heartState == "TOOLBAR_HEART_STATE_HEARTED",
        isCreator = author?.get("isCreator").let { (it as? JsonPrimitive)?.booleanOrNull } ?: false,
        isLiked = likeState == "TOOLBAR_LIKE_STATE_LIKED",
        likeParams = surfaceAction("likeCommand"),
        unlikeParams = surfaceAction("unlikeCommand"),
        deleteParams = deleteParams,
        replyParams = replyParams,
    )
}

/**
 * True when a perform_comment_action response carries no failure markers:
 * a bare success, or at least one actionResult reporting STATUS_SUCCEEDED.
 */
internal fun JsonElement.hasSucceededActionResult(): Boolean {
    val results = mutableListOf<JsonObject>()
    findObjectsByKey(this, "actionResult", results)
    return results.isEmpty() || results.any { it["status"].stringOrNull() == "STATUS_SUCCEEDED" }
}

/**
 * Decode a URL-encoded, URL-safe base64 InnerTube params blob into a
 * string for substring matching (embedded ids are plain ASCII).
 */
private fun decodeInnerTubeParams(params: String): String? = try {
    val unescaped = URLDecoder.decode(params, "UTF-8")
    val bytes = Base64.decode(unescaped, Base64.URL_SAFE)
    String(bytes, Charsets.ISO_8859_1)
} catch (e: Exception) {
    null
}

private fun findObjectsByKey(node: JsonElement?, key: String, results: MutableList<JsonObject>) {
    when (node) {
        is JsonObject -> {
            node[key].objectOrNull()?.let { results.add(it) }
            node.values.forEach { child ->
                if (child is JsonObject || child is JsonArray) findObjectsByKey(child, key, results)
            }
        }
        is JsonArray -> node.forEach { findObjectsByKey(it, key, results) }
        else -> Unit
    }
}

private fun findContinuationTokens(node: JsonElement?, results: MutableList<String>) {
    if (node is JsonObject) {
        node["nextContinuationData"].objectOrNull()
            ?.get("continuation").stringOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                results.add(it)
                return
            }
        node["continuationEndpoint"].objectOrNull()
            ?.get("continuationCommand").objectOrNull()
            ?.get("token").stringOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                results.add(it)
                return
            }
        node["continuationCommand"].objectOrNull()
            ?.get("token").stringOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                results.add(it)
                return
            }
        node.values.forEach { findContinuationTokens(it, results) }
    } else if (node is JsonArray) {
        node.forEach { findContinuationTokens(it, results) }
    }
}

private fun JsonElement?.largestThumbnailUrl(): String? {
    val thumbnails = objectOrNull()?.get("thumbnails").arrayOrNull()
        ?: arrayOrNull()
        ?: return null
    return thumbnails.maxByOrNull { thumbnail ->
        val objectValue = thumbnail.objectOrNull()
        val width = (objectValue?.get("width") as? JsonPrimitive)?.intOrNull ?: 0
        val height = (objectValue?.get("height") as? JsonPrimitive)?.intOrNull ?: 0
        width.toLong() * height.toLong()
    }?.objectOrNull()?.let { thumbnail ->
        thumbnail["url"].stringOrNull() ?: thumbnail["uri"].stringOrNull()
    }
}

private fun normalizeCommentImageUrl(url: String): String {
    val unescaped = url.replace("\\/", "/")
    return if (unescaped.startsWith("//")) "https:$unescaped" else unescaped
}

private fun parseCount(element: JsonElement?): Int {
    val primitive = element as? JsonPrimitive
    primitive?.intOrNull?.let { return it.coerceAtLeast(0) }
    primitive?.longOrNull?.let { return it.coerceIn(0, Int.MAX_VALUE.toLong()).toInt() }
    return parseYouTubeViewCount(element.youtubeText() ?: primitive?.content)
        .coerceIn(0, Int.MAX_VALUE.toLong())
        .toInt()
}
