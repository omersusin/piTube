package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

data class UserBrain(
    val topicScores: Map<String, Double> = emptyMap(),
    val channelScores: Map<String, Double> = emptyMap(),
    val totalInteractions: Int = 0,
    val consecutiveSkips: Int = 0,
    val preferredTopics: Set<String> = emptySet(),
    val blockedTopics: Set<String> = emptySet(),
    val blockedChannels: Set<String> = emptySet(),
    val watchHistory: Map<String, Float> = emptyMap(),
    val impressionCount: Map<String, Int> = emptyMap(),
    val topicAffinities: Map<String, Double> = emptyMap(),
    val hasCompletedOnboarding: Boolean = false
)

enum class InteractionType { CLICK, LIKED, WATCHED, SKIPPED, DISLIKED }

object FlowNeuroEngine {
    private const val FILE_NAME = "neuro_brain.json"
    private val gson = Gson()
    private var brain: UserBrain? = null

    private fun getFile(context: Context) = File(context.filesDir, FILE_NAME)

    fun initialize(context: Context) {
        if (brain != null) return
        val file = getFile(context)
        if (file.exists()) {
            try {
                val type = object : TypeToken<UserBrain>() {}.type
                brain = gson.fromJson(file.readText(), type)
            } catch (e: Exception) {
                brain = UserBrain()
            }
        } else {
            brain = UserBrain()
        }
    }

    private fun save(context: Context) {
        getFile(context).writeText(gson.toJson(brain ?: UserBrain()))
    }

    fun getBrain(): UserBrain = brain ?: UserBrain()

    suspend fun recordInteraction(
        context: Context,
        videoId: String,
        title: String,
        channelName: String,
        channelId: String?,
        type: InteractionType,
        percentWatched: Float = 0f
    ) = withContext(Dispatchers.IO) {
        initialize(context)
        val current = brain ?: return@withContext
        val topics = extractTopics(title, channelName)
        
        val learningRate = when (type) {
            InteractionType.CLICK -> 0.03
            InteractionType.LIKED -> 0.30
            InteractionType.WATCHED -> 0.15 * percentWatched
            InteractionType.SKIPPED -> -0.15
            InteractionType.DISLIKED -> -0.40
        }

        val newTopicScores = NeuroVectorMath.adjustVector(
            current.topicScores.toMutableMap(),
            topics.associateWith { 1.0 },
            learningRate
        )

        val newChannelScores = current.channelScores.toMutableMap()
        val channelKey = channelName.lowercase()
        val currentScore = newChannelScores[channelKey] ?: 0.5
        val outcome = if (learningRate > 0) 1.0 else 0.0
        newChannelScores[channelKey] = (currentScore * 0.9) + (outcome * 0.1)

        val newWatchHistory = current.watchHistory.toMutableMap()
        if (type == InteractionType.WATCHED && percentWatched > 0.15f) {
            newWatchHistory[videoId] = max(newWatchHistory[videoId] ?: 0f, percentWatched)
        }

        val newConsecutiveSkips = when (type) {
            InteractionType.CLICK, InteractionType.LIKED, InteractionType.WATCHED -> 0
            InteractionType.SKIPPED, InteractionType.DISLIKED -> min(current.consecutiveSkips + 1, 30)
        }

        val newAffinities = current.topicAffinities.toMutableMap()
        if (learningRate > 0 && topics.size >= 2) {
            for (i in topics.indices) {
                for (j in i + 1 until topics.size) {
                    val key = if (topics[i] < topics[j]) "${topics[i]}|${topics[j]}" else "${topics[j]}|${topics[i]}"
                    newAffinities[key] = min(1.0, (newAffinities[key] ?: 0.0) + 0.01)
                }
            }
        }

        brain = current.copy(
            topicScores = newTopicScores,
            channelScores = newChannelScores,
            totalInteractions = current.totalInteractions + 1,
            consecutiveSkips = newConsecutiveSkips,
            watchHistory = newWatchHistory,
            topicAffinities = newAffinities
        )
        save(context)
    }

    suspend fun recordImpression(context: Context, videoId: String) = withContext(Dispatchers.IO) {
        initialize(context)
        val current = brain ?: return@withContext
        val newImpressions = current.impressionCount.toMutableMap()
        newImpressions[videoId] = (newImpressions[videoId] ?: 0) + 1
        brain = current.copy(impressionCount = newImpressions)
        save(context)
    }

    suspend fun rank(context: Context, candidates: List<VideoItem>): List<VideoItem> = withContext(Dispatchers.Default) {
        initialize(context)
        val current = brain ?: return@withContext candidates

        val boredomFactor = (current.consecutiveSkips / 20.0).coerceIn(0.0, 0.5)
        val wPersonality = 0.2 - (boredomFactor * 0.2) // Further reduced
        val wNovelty = 0.4 + boredomFactor

        // Track channel appearances for diversity enforcement
        val channelCounts = mutableMapOf<String, Int>()
        val result = mutableListOf<VideoItem>()

        // Sort by score first, then enforce diversity
        val scored = candidates.shuffled().map { video ->
            val videoTopics = extractTopics(video.title, video.uploaderName)
            val videoVector = videoTopics.associateWith { 1.0 }
            val personalityScore = NeuroVectorMath.calculateCosineSimilarity(current.topicScores, videoVector)
            
            val noveltyScore = 1.0 - personalityScore
            
            var totalScore = (personalityScore * wPersonality) + (noveltyScore * wNovelty)

            // Very small channel preference boost
            val channelName = video.uploaderName.lowercase()
            if (current.channelScores.containsKey(channelName)) {
                val channelScore = current.channelScores[channelName] ?: 0.5
                totalScore += (channelScore - 0.5) * 0.02 // Minimal boost
            }

            val watched = current.watchHistory[video.videoId] ?: 0f
            if (watched > 0.85f) totalScore *= 0.02
            else if (watched > 0.50f) totalScore *= 0.30
            else if (watched > 0.15f) totalScore *= 0.70

            val impressions = current.impressionCount[video.videoId] ?: 0
            if (impressions >= 5) totalScore *= 0.05
            else if (impressions >= 3) totalScore *= 0.15
            else if (impressions >= 1) totalScore *= 0.35

            if (videoTopics.size >= 2) {
                var affinityBoost = 0.0
                for (i in videoTopics.indices) {
                    for (j in i + 1 until videoTopics.size) {
                        val key = if (videoTopics[i] < videoTopics[j]) "${videoTopics[i]}|${videoTopics[j]}" else "${videoTopics[j]}|${videoTopics[i]}"
                        affinityBoost += (current.topicAffinities[key] ?: 0.0) * 0.02
                    }
                }
                totalScore += affinityBoost.coerceAtMost(0.05)
            }

            video to totalScore
        }.sortedByDescending { it.second }

        // Enforce strict channel diversity: max 2 videos per channel in top 20
        for ((video, score) in scored) {
            val channelName = video.uploaderName.lowercase()
            val count = channelCounts.getOrDefault(channelName, 0)
            
            if (count >= 2 && result.size < 20) {
                // Skip this video if we already have 2 from this channel
                continue
            }
            
            channelCounts[channelName] = count + 1
            result.add(video)
        }

        result
    }

    private fun extractTopics(title: String, channelName: String): List<String> {
        val stopWords = setOf("the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "is", "it", "this", "that", "my", "your", "his", "her", "its", "our", "their", "we", "you", "they", "he", "she", "me", "him", "us", "them", "i", "am", "are", "was", "were", "be", "been", "being", "have", "has", "had", "do", "does", "did", "will", "would", "could", "should", "may", "might", "must", "can", "not", "no", "yes", "so", "if", "then", "than", "when", "where", "why", "how", "what", "which", "who", "whom", "whose", "from", "up", "down", "out", "off", "over", "under", "again", "further", "once", "here", "there", "all", "each", "every", "both", "few", "more", "most", "other", "some", "such", "only", "own", "same", "just", "also", "as", "into", "through", "during", "before", "after", "above", "below", "between", "any", "because", "about", "against", "until")
        
        val words = (title + " " + channelName)
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 && it !in stopWords }
            .distinct()
        
        return words.take(10)
    }

    suspend fun completeOnboarding(context: Context, selectedTopics: Set<String>) = withContext(Dispatchers.IO) {
        initialize(context)
        val current = brain ?: return@withContext
        val topicWeights = mutableMapOf<String, Double>()
        selectedTopics.forEachIndexed { index, topic ->
            val weight = when {
                index < 3 -> 0.55
                index < 6 -> 0.40
                else -> 0.30
            }
            topicWeights[topic.lowercase()] = weight
        }
        brain = current.copy(
            topicScores = topicWeights,
            preferredTopics = selectedTopics,
            hasCompletedOnboarding = true
        )
        save(context)
    }

    suspend fun needsOnboarding(context: Context): Boolean = withContext(Dispatchers.IO) {
        initialize(context)
        val current = brain ?: return@withContext true
        !current.hasCompletedOnboarding && current.totalInteractions < 5
    }

    suspend fun addPreferredTopic(context: Context, topic: String) = withContext(Dispatchers.IO) {
        initialize(context)
        val current = brain ?: return@withContext
        brain = current.copy(preferredTopics = current.preferredTopics + topic.lowercase())
        save(context)
    }

    suspend fun removePreferredTopic(context: Context, topic: String) = withContext(Dispatchers.IO) {
        initialize(context)
        val current = brain ?: return@withContext
        brain = current.copy(preferredTopics = current.preferredTopics - topic.lowercase())
        save(context)
    }

    suspend fun addBlockedTopic(context: Context, topic: String) = withContext(Dispatchers.IO) {
        initialize(context)
        val current = brain ?: return@withContext
        brain = current.copy(blockedTopics = current.blockedTopics + topic.lowercase())
        save(context)
    }

    suspend fun removeBlockedTopic(context: Context, topic: String) = withContext(Dispatchers.IO) {
        initialize(context)
        val current = brain ?: return@withContext
        brain = current.copy(blockedTopics = current.blockedTopics - topic.lowercase())
        save(context)
    }

    suspend fun blockChannel(context: Context, channelId: String) = withContext(Dispatchers.IO) {
        initialize(context)
        val current = brain ?: return@withContext
        val newChannelScores = current.channelScores.toMutableMap()
        newChannelScores.remove(channelId)
        brain = current.copy(blockedChannels = current.blockedChannels + channelId, channelScores = newChannelScores)
        save(context)
    }

    suspend fun unblockChannel(context: Context, channelId: String) = withContext(Dispatchers.IO) {
        initialize(context)
        val current = brain ?: return@withContext
        brain = current.copy(blockedChannels = current.blockedChannels - channelId)
        save(context)
    }

    suspend fun reset(context: Context) = withContext(Dispatchers.IO) {
        brain = UserBrain()
        save(context)
    }

    suspend fun exportBrain(context: Context): String = withContext(Dispatchers.IO) {
        initialize(context)
        gson.toJson(brain ?: UserBrain())
    }

    suspend fun importBrain(context: Context, json: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val type = object : TypeToken<UserBrain>() {}.type
            brain = gson.fromJson(json, type)
            save(context)
            true
        } catch (e: Exception) {
            false
        }
    }
}
