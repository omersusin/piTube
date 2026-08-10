package com.omersusin.pitube.widget.core

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.omersusin.pitube.widget.downloads.DownloadsWidget
import com.omersusin.pitube.widget.quickactions.QuickActionsWidget
import com.omersusin.pitube.widget.recent.RecentlyPlayedWidget

/** Registry of every Flow widget — used to re-render all of them on app theme changes. */
object FlowWidgets {
    suspend fun updateAll(context: Context) {
        QuickActionsWidget().updateAll(context)
        RecentlyPlayedWidget().updateAll(context)
        DownloadsWidget().updateAll(context)
    }
}
