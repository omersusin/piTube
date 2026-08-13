package com.omersusin.pitube.innertube.pages

import com.omersusin.pitube.innertube.models.Menu

object PageHelper {
    fun extractFeedbackToken(menu: Menu.MenuRenderer.Item.ToggleMenuServiceRenderer?, type: String): String? {
        if (menu == null) return null
        val defaultToken = menu.defaultServiceEndpoint.feedbackEndpoint?.feedbackToken
        val toggledToken = menu.toggledServiceEndpoint?.feedbackEndpoint?.feedbackToken

        return if (menu.defaultIcon.iconType == type) {
            defaultToken
        } else {
            toggledToken
        }
    }
}
