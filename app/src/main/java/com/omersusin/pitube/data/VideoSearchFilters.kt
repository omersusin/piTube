package com.omersusin.pitube.data

import java.time.LocalDate

enum class VideoSearchDateFilter(val label: String, private val days: Long?) {
    ANY("Any time", null),
    WEEK("This week", 7),
    MONTH("This month", 31),
    SIX_MONTHS("6 months", 183),
    YEAR("This year", 366);

    fun applyTo(query: String): String {
        val d = days ?: return query
        return "$query after:${LocalDate.now().minusDays(d)}"
    }
}

enum class VideoSearchSort(val label: String, val code: Int) {
    RELEVANCE("Relevance", 0),
    UPLOAD_DATE("Newest", 2),
    VIEW_COUNT("Most viewed", 3),
    RATING("Top rated", 1)
}
