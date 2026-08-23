package com.omersusin.pitube.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Runs(
    val runs: List<Run>?,
)

@Serializable
data class Run(
    val text: String,
    val navigationEndpoint: NavigationEndpoint?,
)

fun List<List<Run>>.clean(): List<List<Run>> =
    if (getOrNull(0)?.getOrNull(0)?.navigationEndpoint != null ||
        (getOrNull(0)?.getOrNull(0)?.text?.contains(regex = Regex("[&,]"))) != false
    ) {
        this
    } else {
        this.drop(1)
    }

fun List<Run>.oddElements() =
    filterIndexed { index, _ ->
        index % 2 == 0
    }
