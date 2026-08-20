package com.omersusin.pitube.ui.screens.subscriptions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omersusin.pitube.data.local.ChannelSubscription
import com.omersusin.pitube.data.local.SubscriptionRepository
import com.omersusin.pitube.data.local.entity.SubscriptionGroupEntity
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.data.repository.YouTubeRepository
import com.omersusin.pitube.innertube.YouTube
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val youtubeRepository: YouTubeRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val subscribedChannels: StateFlow<List<ChannelSubscription>> =
        subscriptionRepository.getAllSubscriptions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups: StateFlow<List<SubscriptionGroupEntity>> =
        subscriptionRepository.getAllGroups()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedGroupId = MutableStateFlow<String?>(null)
    val selectedGroupId: StateFlow<String?> = _selectedGroupId.asStateFlow()

    private val _subscriptionFeed = MutableStateFlow<List<Video>>(emptyList())
    val subscriptionFeed: StateFlow<List<Video>> = _subscriptionFeed.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)
    val progress: StateFlow<Pair<Int, Int>?> = _progress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            subscribedChannels.collect { channels ->
                if (channels.isNotEmpty() && _subscriptionFeed.value.isEmpty() && !_isLoading.value) {
                    refresh()
                }
            }
        }
        refresh()
    }

    fun selectGroup(groupId: String?) {
        if (_selectedGroupId.value == groupId) return
        _selectedGroupId.value = groupId
        refresh(force = true)
    }

    fun refresh(force: Boolean = false) {
        if (_isLoading.value && !force) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val allChannels = try {
                    subscriptionRepository.getAllSubscriptions().first()
                } catch (_: Exception) { subscribedChannels.value }
                val filteredIds = when (val gid = _selectedGroupId.value) {
                    null -> allChannels.map { it.channelId }
                    else -> {
                        val inGroup = subscriptionRepository.channelsInGroup(gid)
                        if (inGroup.isEmpty()) emptyList() else inGroup.toList()
                    }
                }
                if (filteredIds.isEmpty()) {
                    _subscriptionFeed.value = emptyList()
                    _isLoading.value = false
                    _progress.value = null
                    return@launch
                }
                val signedIn = !YouTube.cookie.isNullOrBlank()
                val accountFeed = if (signedIn && _selectedGroupId.value == null) {
                    YouTube.webSubscriptionsFeed().getOrNull()?.videos.orEmpty()
                } else emptyList()
                _progress.value = 0 to filteredIds.size
                val localFeed = youtubeRepository.getSubscriptionFeed(filteredIds)
                _progress.value = null
                _subscriptionFeed.value = mergeFeeds(accountFeed, localFeed)
                if (_subscriptionFeed.value.isEmpty()) {
                    _error.value = null
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Yüklenemedi"
            } finally {
                _isLoading.value = false
                _progress.value = null
            }
        }
    }

    private fun mergeFeeds(accountFeed: List<Video>, localFeed: List<Video>): List<Video> {
        if (localFeed.isEmpty()) return accountFeed
        if (accountFeed.isEmpty()) return localFeed
        return (accountFeed + localFeed).distinctBy { it.id }.sortedByDescending { it.timestamp }
    }

    fun createGroup(name: String) {
        viewModelScope.launch { subscriptionRepository.createGroup(name) }
    }

    fun renameGroup(id: String, name: String) {
        viewModelScope.launch { subscriptionRepository.renameGroup(id, name) }
    }

    fun deleteGroup(id: String) {
        viewModelScope.launch {
            subscriptionRepository.deleteGroup(id)
            if (_selectedGroupId.value == id) _selectedGroupId.value = null
        }
    }

    fun toggleChannelInGroup(groupId: String, channelId: String) {
        viewModelScope.launch {
            subscriptionRepository.toggleChannelInGroup(groupId, channelId)
            if (_selectedGroupId.value == groupId) refresh(force = true)
        }
    }

    fun isSubscribed(channelId: String): Boolean =
        subscribedChannels.value.any { it.channelId == channelId }
}
