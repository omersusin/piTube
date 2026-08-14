package com.omersusin.pitube.ui.screens.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omersusin.pitube.data.local.NotificationRepository
import com.omersusin.pitube.data.local.NotificationSync
import com.omersusin.pitube.data.local.entity.NotificationEntity
import com.omersusin.pitube.innertube.YouTube
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: NotificationRepository,
) : ViewModel() {

    val notifications: StateFlow<List<NotificationEntity>> = repository.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount: StateFlow<Int> = repository.unreadCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _refreshFailed = MutableStateFlow(false)
    val refreshFailed: StateFlow<Boolean> = _refreshFailed

    /**
     * Fetch the signed-in user's notification inbox from YouTube and write it
     * to the local database. A no-op when not signed in.
     */
    fun refreshNotifications() {
        viewModelScope.launch {
            if (YouTube.cookie.isNullOrBlank()) return@launch
            _isRefreshing.value = true
            _refreshFailed.value = false
            runCatching { NotificationSync.sync(context) }
                .onFailure {
                    _refreshFailed.value = true
                }
            _isRefreshing.value = false
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            repository.markAllAsRead()
        }
    }

    fun deleteNotification(notification: NotificationEntity) {
        viewModelScope.launch {
            repository.deleteNotification(notification)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }
}
