package com.omersusin.pitube.translation

import kotlinx.serialization.Serializable

@Serializable
data class Translation(
    val translatedText: String,
    val detectedLanguage: String? = null,
    val transliterations: List<String>? = null,
    val definitions: List<Definition>? = null,
    val similar: List<String>? = null,
    val examples: List<String>? = null,
    val alternativeTranslations: List<String>? = null,
)

@Serializable
data class Definition(
    val type: String? = null,
    val definition: String? = null,
    val example: String? = null,
    val synonym: String? = null,
)