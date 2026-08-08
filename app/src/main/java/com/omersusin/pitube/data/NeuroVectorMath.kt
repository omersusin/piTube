package com.omersusin.pitube.data

import kotlin.math.abs
import kotlin.math.sqrt

object NeuroVectorMath {
    const val TOPIC_SIMILARITY_WEIGHT = 0.70
    const val DURATION_SIMILARITY_WEIGHT = 0.10
    const val PACING_SIMILARITY_WEIGHT = 0.10
    const val COMPLEXITY_SIMILARITY_WEIGHT = 0.10
    const val TOPIC_PRUNE_THRESHOLD = 0.03
    const val SCALAR_ONLY_DAMP = 0.3

    fun calculateCosineSimilarity(user: Map<String, Double>, content: Map<String, Double>): Double {
        if (user.isEmpty() || content.isEmpty()) return 0.0
        
        var dotProduct = 0.0
        user.forEach { (key, value) ->
            content[key]?.let { dotProduct += value * it }
        }
        
        var magnitudeA = 0.0
        var magnitudeB = 0.0
        user.values.forEach { magnitudeA += it * it }
        content.values.forEach { magnitudeB += it * it }
        
        return if (magnitudeA > 0 && magnitudeB > 0) {
            dotProduct / (sqrt(magnitudeA) * sqrt(magnitudeB))
        } else 0.0
    }

    fun adjustVector(current: MutableMap<String, Double>, target: Map<String, Double>, baseRate: Double): Map<String, Double> {
        val result = current.toMutableMap()
        val isNegative = baseRate < 0

        target.forEach { (key, targetVal) ->
            val currentVal = result[key] ?: 0.0
            val delta = if (isNegative) {
                val proportional = currentVal * currentVal * baseRate
                minOf(proportional, baseRate * 0.3)
            } else {
                val saturationPenalty = (1.0 - currentVal) * (1.0 - currentVal)
                (targetVal - currentVal) * baseRate * saturationPenalty
            }
            result[key] = (currentVal + delta).coerceIn(0.0, 1.0)
        }

        if (baseRate > 0) {
            val iterator = result.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (!target.containsKey(entry.key)) {
                    val decayRate = when {
                        entry.value >= 0.30 -> 0.998
                        entry.value >= 0.10 -> 0.993
                        else -> 0.97
                    }
                    entry.setValue(entry.value * decayRate)
                }
                if (!target.containsKey(entry.key) && entry.value < TOPIC_PRUNE_THRESHOLD) {
                    iterator.remove()
                }
            }
        }

        return result
    }

    fun calculateTitleSimilarity(tokens1: Set<String>, tokens2: Set<String>): Double {
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0
        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size
        return if (union > 0) intersection.toDouble() / union else 0.0
    }
}
