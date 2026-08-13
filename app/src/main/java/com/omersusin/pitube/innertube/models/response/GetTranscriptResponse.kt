package com.omersusin.pitube.innertube.models.response

import com.omersusin.pitube.innertube.models.Runs
import kotlinx.serialization.Serializable

@Serializable
data class GetTranscriptResponse(
    val actions: List<Action>?,
) {
    @Serializable
    data class Action(
        val updateEngagementPanelAction: UpdateEngagementPanelAction?,
    ) {
        @Serializable
        data class UpdateEngagementPanelAction(
            val content: Content?,
        ) {
            @Serializable
            data class Content(
                val transcriptRenderer: TranscriptRenderer?,
            ) {
                @Serializable
                data class TranscriptRenderer(
                    val body: Body?,
                ) {
                    @Serializable
                    data class Body(
                        val transcriptBodyRenderer: TranscriptBodyRenderer?,
                    ) {
                        @Serializable
                        data class TranscriptBodyRenderer(
                            val cueGroups: List<CueGroup>?,
                        ) {
                            @Serializable
                            data class CueGroup(
                                val transcriptCueGroupRenderer: CueGroupRenderer?,
                            ) {
                                @Serializable
                                data class CueGroupRenderer(
                                    val cues: List<Cue>?,
                                ) {
                                    @Serializable
                                    data class Cue(
                                        val transcriptCueRenderer: CueRenderer?,
                                    ) {
                                        @Serializable
                                        data class CueRenderer(
                                            val cue: Runs?,
                                            val startOffsetMs: Long = 0L,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}