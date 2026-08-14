package com.omersusin.pitube.translation

import kotlinx.serialization.Serializable

@Serializable
data class Language(
    val code: String,
    val name: String,
)