package com.omersusin.pitube.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A cached finished translation, so repeat viewings never hit the provider
 * for the same (engine, target language, source text) triple.
 *
 * [id] is a stable hash of engine + target + normalized source text.
 */
@Entity(tableName = "cached_translations")
data class CachedTranslationEntity(
    @PrimaryKey
    val id: String,
    val engine: String,
    val targetLanguage: String,
    val sourceText: String,
    val translatedText: String,
    val createdAt: Long = System.currentTimeMillis(),
)