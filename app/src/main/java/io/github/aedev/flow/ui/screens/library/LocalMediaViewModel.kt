package io.github.aedev.flow.ui.screens.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LocalMediaItem(
    val id: Long,
    val contentUri: String,
    val title: String,
    val subtitle: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val isVideo: Boolean,
    val artworkUri: String? = null
)

data class LocalMediaUiState(
    val videos: List<LocalMediaItem> = emptyList(),
    val isScanning: Boolean = false,
    val hasScanned: Boolean = false,
    val permissionDenied: Boolean = false
)

@HiltViewModel
class LocalMediaViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalMediaUiState())
    val uiState: StateFlow<LocalMediaUiState> = _uiState.asStateFlow()

    fun scan() {
        if (_uiState.value.isScanning) return
        _uiState.update { it.copy(isScanning = true, permissionDenied = false) }
        viewModelScope.launch {
            val videos = withContext(PerformanceDispatcher.diskIO) { queryVideos() }
            _uiState.update {
                it.copy(videos = videos, isScanning = false, hasScanned = true)
            }
        }
    }

    fun onPermissionDenied() {
        _uiState.update { it.copy(permissionDenied = true, isScanning = false, hasScanned = true) }
    }

    private fun queryVideos(): List<LocalMediaItem> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )
        val result = ArrayList<LocalMediaItem>()
        try {
            appContext.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val size = cursor.getLong(sizeCol)
                    if (size <= 0L) continue
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                        ?: cursor.getString(nameCol)?.substringBeforeLast('.')
                        ?: continue
                    val uri = ContentUris.withAppendedId(collection, id)
                    result += LocalMediaItem(
                        id = id,
                        contentUri = uri.toString(),
                        title = title,
                        subtitle = cursor.getString(bucketCol)?.takeIf { it.isNotBlank() } ?: "",
                        durationMs = cursor.getLong(durCol),
                        sizeBytes = size,
                        isVideo = true
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryVideos failed", e)
        }
        return result
    }

    companion object {
        private const val TAG = "LocalMediaViewModel"

        fun localMediaId(item: LocalMediaItem): String = "local_${item.id}"
    }
}
