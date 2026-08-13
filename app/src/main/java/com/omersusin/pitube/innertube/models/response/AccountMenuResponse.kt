package com.omersusin.pitube.innertube.models.response

import com.omersusin.pitube.innertube.models.AccountInfo
import com.omersusin.pitube.innertube.models.Runs
import com.omersusin.pitube.innertube.models.Thumbnails
import com.omersusin.pitube.innertube.models.Thumbnail
import kotlinx.serialization.Serializable

@Serializable
data class AccountMenuResponse(
    val actions: List<Action>,
) {
    @Serializable
    data class Action(
        val openPopupAction: OpenPopupAction,
    ) {
        @Serializable
        data class OpenPopupAction(
            val popup: Popup,
        ) {
            @Serializable
            data class Popup(
                val multiPageMenuRenderer: MultiPageMenuRenderer,
            ) {
                @Serializable
                data class MultiPageMenuRenderer(
                    val header: Header?,
                ) {
                    @Serializable
                    data class Header(
                        val activeAccountHeaderRenderer: ActiveAccountHeaderRenderer,
                    ) {
                        @Serializable
                        data class ActiveAccountHeaderRenderer(
                            val accountName: Runs,
                            val email: Runs?,
                            val channelHandle: Runs?,
                            val accountPhoto: Thumbnails,
                        ) {
                            fun toAccountInfo() =
                                AccountInfo(
                                    name = accountName.runs?.firstOrNull()?.text.orEmpty(),
                                    email = email?.runs?.firstOrNull()?.text,
                                    channelHandle = channelHandle?.runs?.firstOrNull()?.text,
                                    thumbnailUrl = accountPhoto.thumbnails.lastOrNull()?.url
                                        ?.let { if (it.startsWith("//")) "https:$it" else it }
                                        ?.let(::upgradeAvatarResolution),
                                )

                            /** Bump small avatar sizes (=s96 etc.) to 512px like Koda does. */
                            private fun upgradeAvatarResolution(url: String): String {
                                val resized = url.replace(Regex("=s\\d+"), "=s512")
                                return if (resized.contains("=s512")) resized else "$resized=s512"
                            }
                        }
                    }
                }
            }
        }
    }
}
