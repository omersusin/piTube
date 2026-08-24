package com.omersusin.pitube.ui.screens.likedvideos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.omersusin.pitube.data.local.LikedVideoInfo
import com.omersusin.pitube.data.local.LikedVideosRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LikedSort {
    NEWEST,
    OLDEST,
    TITLE,
}

enum class LikedTypeFilter {
    ALL,
    MUSIC,
}

@HiltViewModel
class LikedVideosViewModel
    @Inject
    constructor(
        private val likedVideosRepository: LikedVideosRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(LikedVideosUiState())
        val uiState: StateFlow<LikedVideosUiState> = _uiState.asStateFlow()

        private val sort = MutableStateFlow(LikedSort.NEWEST)
        private val typeFilter = MutableStateFlow(LikedTypeFilter.ALL)

        init {
            // Load all likes so the screen can switch between videos and music locally.
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                combine(
                    likedVideosRepository.getAllLikedVideos(),
                    sort,
                    typeFilter,
                ) { likedVideos, currentSort, currentFilter ->
                    val filtered =
                        when (currentFilter) {
                            LikedTypeFilter.ALL -> likedVideos
                            LikedTypeFilter.MUSIC -> likedVideos.filter { it.isMusic }
                        }
                    val sorted =
                        when (currentSort) {
                            LikedSort.NEWEST -> filtered.sortedByDescending { it.likedAt }
                            LikedSort.OLDEST -> filtered.sortedBy { it.likedAt }
                            LikedSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
                        }
                    sorted
                }.collect { visibleLikes ->
                    _uiState.update {
                        it.copy(
                            likedVideos = visibleLikes,
                            typeFilter = typeFilter.value,
                            isLoading = false,
                        )
                    }
                }
            }
        }

        fun setSort(newSort: LikedSort) {
            sort.value = newSort
        }

        fun setTypeFilter(newFilter: LikedTypeFilter) {
            typeFilter.value = newFilter
        }

        fun removeLike(videoId: String) {
            viewModelScope.launch {
                likedVideosRepository.removeLikeState(videoId)
            }
        }
    }

data class LikedVideosUiState(
    val likedVideos: List<LikedVideoInfo> = emptyList(),
    val isLoading: Boolean = false,
    val typeFilter: LikedTypeFilter = LikedTypeFilter.ALL,
)
