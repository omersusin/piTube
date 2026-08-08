package com.omersusin.pitube.data

import androidx.compose.runtime.mutableStateListOf

object DownloadTracker {
    data class DownloadItem(val id: String, val title: String, var progress: Int, var status: String)
    val items = mutableStateListOf<DownloadItem>()
    fun start(id: String, title: String): DownloadItem {
        val item = DownloadItem(id, title, 0, "downloading")
        items.add(0, item)
        return item
    }
}
