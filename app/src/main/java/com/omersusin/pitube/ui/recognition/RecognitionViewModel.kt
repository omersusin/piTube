package com.omersusin.pitube.ui.recognition

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.RecognitionFailureType
import com.omersusin.pitube.recognition.RecognitionException
import com.omersusin.pitube.recognition.RecognitionRepository
import com.omersusin.pitube.recognition.SongRecognitionOutcome
import com.omersusin.pitube.recognition.TrackMatch
import com.omersusin.pitube.recognition.VoiceRecognitionSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RecognitionMode {
    VOICE,
    SONG,
}

enum class RecognitionPhase {
    IDLE,
    LISTENING,
    PROCESSING,
    SUCCESS,
    ERROR,
}

data class RecognitionUiState(
    val mode: RecognitionMode = RecognitionMode.VOICE,
    val phase: RecognitionPhase = RecognitionPhase.IDLE,
    val levels: List<Float> = emptyList(),
    val bands: FloatArray? = null,
    val transcript: String? = null,
    val voiceSource: VoiceRecognitionSource? = null,
    val track: TrackMatch? = null,
    val message: String? = null,
    val recordingSaved: Boolean = false,
    val retryScheduled: Boolean = false,
)

@HiltViewModel
class RecognitionViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val repository = RecognitionRepository(context)

        private val _uiState = MutableStateFlow(RecognitionUiState())
        val uiState: StateFlow<RecognitionUiState> = _uiState.asStateFlow()

        private val interrupted = MutableStateFlow(false)
        private var listeningJob: kotlinx.coroutines.Job? = null

        fun setMode(mode: RecognitionMode) {
            if (_uiState.value.phase == RecognitionPhase.LISTENING || _uiState.value.phase == RecognitionPhase.PROCESSING) return
            _uiState.value = RecognitionUiState(mode = mode)
        }

        fun startListening() {
            if (_uiState.value.phase == RecognitionPhase.LISTENING ||
                _uiState.value.phase == RecognitionPhase.PROCESSING
            ) {
                return
            }
            val mode = _uiState.value.mode
            interrupted.value = false
            _uiState.update {
                it.copy(
                    phase = RecognitionPhase.LISTENING,
                    transcript = null,
                    track = null,
                    message = null,
                    recordingSaved = false,
                    retryScheduled = false,
                    levels = emptyList(),
                    bands = null,
                )
            }
            listeningJob =
                viewModelScope.launch {
                    when (mode) {
                        RecognitionMode.VOICE -> runVoice()
                        RecognitionMode.SONG -> runSong()
                    }
                }.apply {
                    invokeOnCompletion { cause ->
                        if (cause is CancellationException && interrupted.value) {
                            _uiState.update { it.copy(phase = RecognitionPhase.IDLE) }
                        }
                    }
                }
        }

        fun stopListening() {
            interrupted.value = true
            repository.stopVoiceRecognition()
            listeningJob?.cancel()
        }

        private suspend fun runVoice() {
            try {
                // Uses the selected STT provider (Cihaz STT by default), or a
                // cloud provider with automatic on-device fallback. The live
                // mic levels keep the talking face animating either way.
                val (transcript, source) =
                    repository.recognizeVoice(
                        interrupted = { interrupted.value },
                        onLevel = { level ->
                            _uiState.update { it.copy(levels = (it.levels + level).takeLast(40)) }
                        },
                        onBands = { bands ->
                            _uiState.update { it.copy(bands = bands.copyOf()) }
                        },
                        onProcessing = {
                            _uiState.update { it.copy(phase = RecognitionPhase.PROCESSING) }
                        },
                    )
                if (interrupted.value) {
                    _uiState.update { it.copy(phase = RecognitionPhase.IDLE) }
                    return
                }
                _uiState.update {
                    it.copy(
                        phase = RecognitionPhase.SUCCESS,
                        transcript = transcript,
                        voiceSource = source,
                    )
                }
            } catch (e: RecognitionException) {
                Log.w("Recognition", "Voice recognition failed: ${e.message}")
                if (interrupted.value) {
                    _uiState.update { it.copy(phase = RecognitionPhase.IDLE) }
                } else {
                    _uiState.update {
                        it.copy(
                            phase = RecognitionPhase.ERROR,
                            message = e.message ?: context.getString(R.string.recognition_error_generic),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Recognition", "Unexpected voice recognition failure", e)
                _uiState.update {
                    it.copy(
                        phase = RecognitionPhase.ERROR,
                        message = e.message ?: context.getString(R.string.recognition_error_generic),
                    )
                }
            }
        }

        private suspend fun runSong() {
            val outcome = repository.recognizeSong(
                interrupted = { interrupted.value },
                onLevel = { level ->
                    _uiState.update { it.copy(levels = (it.levels + level).takeLast(40)) }
                },
                onBands = { bands ->
                    _uiState.update { it.copy(bands = bands.copyOf()) }
                },
            )
            if (interrupted.value) {
                _uiState.update { it.copy(phase = RecognitionPhase.IDLE) }
                return
            }

            when (outcome) {
                is SongRecognitionOutcome.Matched -> {
                    _uiState.update {
                        it.copy(phase = RecognitionPhase.SUCCESS, track = outcome.track)
                    }
                }

                is SongRecognitionOutcome.NoMatch -> {
                    _uiState.update {
                        it.copy(
                            phase = RecognitionPhase.ERROR,
                            message = if (outcome.recordingSaved) {
                                context.getString(R.string.recognition_failure_no_match_saved)
                            } else {
                                context.getString(R.string.recognition_failure_no_match)
                            },
                            recordingSaved = outcome.recordingSaved,
                        )
                    }
                }

                is SongRecognitionOutcome.Failed -> {
                    _uiState.update {
                        it.copy(
                            phase = RecognitionPhase.ERROR,
                            message = when (outcome.type) {
                                RecognitionFailureType.BAD_CONNECTION ->
                                    context.getString(R.string.recognition_failure_no_internet)
                                RecognitionFailureType.NO_MATCH ->
                                    context.getString(R.string.recognition_failure_no_match)
                                RecognitionFailureType.OTHER ->
                                    context.getString(R.string.recognition_failure_other)
                            },
                            recordingSaved = outcome.recordingSaved,
                            retryScheduled = outcome.retryScheduled,
                        )
                    }
                }
            }
        }

        fun reset() {
            interrupted.value = true
            repository.stopVoiceRecognition()
            // Cancel the in-flight listening job: without it the old session's
            // recognizer stays bound while the fresh session opens, which is the
            // `ERROR_RECOGNIZER_BUSY` reopen leak.
            listeningJob?.cancel()
            _uiState.value = RecognitionUiState(mode = _uiState.value.mode)
        }
    }