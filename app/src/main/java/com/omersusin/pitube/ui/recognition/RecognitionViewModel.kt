package com.omersusin.pitube.ui.recognition

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omersusin.pitube.recognition.RecognitionRepository
import com.omersusin.pitube.recognition.SongRecognitionOutcome
import com.omersusin.pitube.recognition.TrackMatch
import com.omersusin.pitube.recognition.VoiceRecognitionSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
        @ApplicationContext context: Context,
    ) : ViewModel() {
        private val repository = RecognitionRepository(context)

        private val _uiState = MutableStateFlow(RecognitionUiState())
        val uiState: StateFlow<RecognitionUiState> = _uiState.asStateFlow()

        private val interrupted = MutableStateFlow(false)

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
                )
            }
            viewModelScope.launch {
                when (mode) {
                    RecognitionMode.VOICE -> runVoice()
                    RecognitionMode.SONG -> runSong()
                }
            }
        }

        fun stopListening() {
            interrupted.value = true
            repository.stopRecording()
        }

        private suspend fun runVoice() {
            val captured = repository.recordVoice(
                interrupted = { interrupted.value },
                onLevel = { level ->
                    _uiState.update { it.copy(levels = (it.levels + level).takeLast(40)) }
                },
            )
            if (interrupted.value) {
                _uiState.update { it.copy(phase = RecognitionPhase.IDLE) }
                return
            }

            // Stay in LISTENING (face keeps animating) while the transcript is
            // produced: the on-device fallback runs its own live mic session,
            // so its RMS levels are forwarded here to keep the visual live.
            val (transcript, source) =
                repository.recognizeVoice(
                    captured,
                    onFallbackLevel = { level ->
                        _uiState.update { it.copy(levels = (it.levels + level).takeLast(40)) }
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
        }

        private suspend fun runSong() {
            val outcome = repository.recognizeSong(
                interrupted = { interrupted.value },
                onLevel = { level ->
                    _uiState.update { it.copy(levels = (it.levels + level).takeLast(40)) }
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
                                "No match found — recording saved"
                            } else {
                                "No match found"
                            },
                            recordingSaved = outcome.recordingSaved,
                        )
                    }
                }

                is SongRecognitionOutcome.Failed -> {
                    _uiState.update {
                        it.copy(
                            phase = RecognitionPhase.ERROR,
                            message = outcome.message,
                            recordingSaved = outcome.recordingSaved,
                            retryScheduled = outcome.retryScheduled,
                        )
                    }
                }
            }
        }

        fun reset() {
            interrupted.value = true
            repository.stopRecording()
            _uiState.value = RecognitionUiState(mode = _uiState.value.mode)
        }
    }