package com.omersusin.pitube.utils

import org.schabi.newpipe.extractor.Image

fun List<Image>?.bestImageUrl(): String =
    this.orEmpty()
        .maxByOrNull { maxOf(it.width, it.height) }
        ?.url
        .orEmpty()

fun List<Image>?.distinctBestImageUrls(limit: Int = 2): List<String> =
    this.orEmpty()
        .asSequence()
        .filter { !it.url.isNullOrBlank() }
        .sortedByDescending { maxOf(it.width, it.height) }
        .distinctBy { it.url.avatarImageIdentityKey() }
        .mapNotNull { it.url }
        .take(limit)
        .toList()

private val AVATAR_IDENTITY_REGEX = Regex("=s\\d+.*$")

internal fun String?.avatarImageIdentityKey(): String =
    orEmpty()
        .substringBefore("?")
        .replace(AVATAR_IDENTITY_REGEX, "")
