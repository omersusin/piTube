package com.omersusin.pitube.innertube.models.response

import com.omersusin.pitube.innertube.models.AccountInfo
import com.omersusin.pitube.innertube.models.Run
import com.omersusin.pitube.innertube.models.Runs
import com.omersusin.pitube.innertube.models.Thumbnails
import com.omersusin.pitube.innertube.models.Thumbnail
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

    companion object {
        /**
         * Lenient JSON-object → model mapper used when strict kotlinx.serialization
         * decoding fails (the account_menu response shape varies by client/account).
         * Walks the canonical actions→openPopupAction→popup→multiPageMenuRenderer→
         * header→activeAccountHeaderRenderer path.
         */
        fun toAccountMenuResponseOrNull(root: JsonObject): AccountMenuResponse? {
            val actionsArr = root["actions"]?.jsonArray ?: return null
            val action = actionsArr.firstOrNull()?.jsonObject ?: return null
            val popup = action["openPopupAction"]?.jsonObject
                ?.get("popup")?.jsonObject
                ?: return null
            val menu = popup["multiPageMenuRenderer"]?.jsonObject ?: return null
            val header = menu["header"]?.jsonObject
                ?.get("activeAccountHeaderRenderer")?.jsonObject
                ?: return null

            fun runsOf(element: JsonElement?): Runs? {
                val runs = element?.jsonObject?.get("runs")?.jsonArray ?: return null
                return Runs(
                    runs.mapNotNull {
                        it.jsonObject.get("text")?.jsonPrimitive?.contentOrNull
                            ?.let { text -> Run(text, navigationEndpoint = null) }
                    }
                )
            }

            fun thumbnailsOf(element: JsonElement?): Thumbnails? {
                val arr = element?.jsonObject?.get("thumbnails")?.jsonArray ?: return null
                return Thumbnails(
                    arr.mapNotNull {
                        val obj = it.jsonObject
                        val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        Thumbnail(
                            url = url,
                            width = obj["width"]?.jsonPrimitive?.intOrNull,
                            height = obj["height"]?.jsonPrimitive?.intOrNull,
                        )
                    }
                )
            }

            // The photo key is usually `accountPhoto`, but some responses call it
            // `avatar` — try both.
            val photo = thumbnailsOf(header["accountPhoto"]) ?: thumbnailsOf(header["avatar"])
                ?: return null

            return AccountMenuResponse(
                actions = listOf(
                    Action(
                        openPopupAction = Action.OpenPopupAction(
                            popup = Action.OpenPopupAction.Popup(
                                multiPageMenuRenderer = Action.OpenPopupAction.Popup.MultiPageMenuRenderer(
                                    header = Action.OpenPopupAction.Popup.MultiPageMenuRenderer.Header(
                                        activeAccountHeaderRenderer = Action.OpenPopupAction.Popup.MultiPageMenuRenderer.Header.ActiveAccountHeaderRenderer(
                                            accountName = runsOf(header["accountName"]) ?: Runs(emptyList()),
                                            email = runsOf(header["email"]),
                                            channelHandle = runsOf(header["channelHandle"]),
                                            accountPhoto = photo,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }

        /** Fallback name extraction when the strict model is unavailable. */
        fun parseNameFromRaw(body: String): String {
            return Regex("\"accountName\"\\s*:\\s*\\{[^}]*?\"text\"\\s*:\\s*\"([^\"]+)\"")
                .find(body)?.groupValues?.get(1)
                .orEmpty()
        }
    }
}
