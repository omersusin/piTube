package io.github.aedev.flow.widget.core

import android.content.Context
import androidx.glance.appwidget.updateAll
import io.github.aedev.flow.widget.downloads.DownloadsWidget
import io.github.aedev.flow.widget.quickactions.QuickActionsWidget
import io.github.aedev.flow.widget.recent.RecentlyPlayedWidget

/** Registry of every Flow widget — used to re-render all of them on app theme changes. */
object FlowWidgets {
    suspend fun updateAll(context: Context) {
        QuickActionsWidget().updateAll(context)
        RecentlyPlayedWidget().updateAll(context)
        DownloadsWidget().updateAll(context)
    }
}
