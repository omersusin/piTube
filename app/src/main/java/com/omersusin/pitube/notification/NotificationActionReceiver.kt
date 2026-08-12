package com.omersusin.pitube.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for handling notification action clicks
 */
class NotificationActionReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_CANCEL_DOWNLOAD = "com.omersusin.pitube.action.CANCEL_DOWNLOAD"
        const val ACTION_DISMISS_NOTIFICATION = "com.omersusin.pitube.action.DISMISS_NOTIFICATION"
        
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_VIDEO_TITLE = "video_title"
        const val EXTRA_DOWNLOAD_ID = "download_id"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("NotificationAction", "Received action: ${intent.action}")
        
        when (intent.action) {
            ACTION_CANCEL_DOWNLOAD -> handleCancelDownload(context, intent)
            ACTION_DISMISS_NOTIFICATION -> handleDismissNotification(context, intent)
        }
    }
    
    private fun handleCancelDownload(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
        
        // Cancel the notification
        if (notificationId != -1) {
            NotificationHelper.cancelNotification(context, notificationId)
        }
        
        // Cancel the actual download task in system DownloadManager
        if (downloadId != -1L) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            downloadManager.remove(downloadId)
            Log.d("NotificationAction", "Download cancelled in system DownloadManager: $downloadId")
        }
    }
    
    private fun handleDismissNotification(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        
        if (notificationId != -1) {
            NotificationHelper.cancelNotification(context, notificationId)
        }
    }
}
