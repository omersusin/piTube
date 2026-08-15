package com.omersusin.pitube.ui.translation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.translation.TranslationController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TranslationEntryPoint {
    fun translationController(): TranslationController
}

/**
 * Composables can't constructor-inject singletons; fetch the shared
 * [TranslationController] through Hilt's application entry point (same
 * pattern as the Glance widgets).
 */
@Composable
fun rememberTranslationController(): TranslationController {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors.fromApplication(context, TranslationEntryPoint::class.java)
            .translationController()
    }
}

/** The evaluated state of one inline text after [rememberTranslatedText]. */
class TranslatedTextState(
    val original: String,
    val translated: String?,
    val mode: String,
    private val translationEnabled: Boolean,
) {
    /** True when double-tapping this text may toggle to the original. */
    val doubleTapEnabled: Boolean
        get() = translationEnabled

    /** True when double-tapping should flip back to [original]. */
    val canToggleOriginal: Boolean
        get() = doubleTapEnabled && isTranslated

    /** True when a real, different translation is available to display. */
    val isTranslated: Boolean
        get() = translated != null && translated.isNotBlank() && translated != original

    /** Whether the surface currently shows the original instead of the translation. */
    var showingOriginal by mutableStateOf(false)
        private set

    /** Flip between the translation and the original (only when meaningful). */
    fun toggleShowingOriginal() {
        if (canToggleOriginal) showingOriginal = !showingOriginal
    }

    /** The text a REPLACE-mode surface should show. Never blank. */
    val displayText: String
        get() = if (showingOriginal) original else translated?.takeIf { it.isNotBlank() } ?: original

    /** True when the surface should render the original below the translation. */
    val showOriginalBelow: Boolean
        get() = mode == "DUAL" && isTranslated && !showingOriginal
}

/**
 * Inline translation hook for any user-facing text. Applies the master
 * toggle, the [feature] toggle (titles / descriptions / comments / ...) and
 * the display mode from settings; [translated] stays null until the provider
 * answers, so surfaces simply keep rendering the original meanwhile. The
 * Room cache makes repeated calls for the same text cheap.
 */
@Composable
fun rememberTranslatedText(
    text: String,
    feature: Flow<Boolean>,
): TranslatedTextState {
    val context = LocalContext.current
    val preferences = remember { PlayerPreferences(context) }
    val controller = rememberTranslationController()

    val masterEnabled by preferences.translationEnabled.collectAsState(initial = false)
    val featureEnabled by feature.collectAsState(initial = false)
    val targetLanguage by preferences.translationTargetLanguage.collectAsState(initial = "")
    val mode by preferences.translationMode.collectAsState(initial = "REPLACE")
    val doubleTapOriginal by preferences.translationDoubleTapOriginal.collectAsState(initial = true)

    var translated by remember(text) { mutableStateOf<String?>(null) }
    val active = masterEnabled && featureEnabled && text.isNotBlank()

    LaunchedEffect(text, targetLanguage, active) {
        translated = if (active) {
            controller.translate(text, targetLanguage.takeIf { it.isNotBlank() })
        } else {
            null
        }
    }

    return remember(text, translated, mode, doubleTapOriginal) {
        val state = TranslatedTextState(
            original = text,
            translated = translated?.trim(),
            mode = mode,
            translationEnabled = masterEnabled && featureEnabled && doubleTapOriginal,
        )
        state
    }
}

/**
 * Double-tap [state] to flip between the translation and the original. The
 * gesture only activates when the double-tap setting is on and the text is
 * actually translated, so untouched surfaces keep their existing behaviour.
 */
fun Modifier.toggleOriginalOnDoubleTap(state: TranslatedTextState): Modifier =
    if (state.canToggleOriginal) {
        pointerInput(state) {
            detectTapGestures(
                onDoubleTap = { state.toggleShowingOriginal() },
            )
        }
    } else {
        this
    }

/**
 * Double-tap [state] to flip between the translation and the original when the
 * text lives inside a [androidx.compose.foundation.text.selection.SelectionContainer.
 * SelectionContainer claims the double-tap for word-select and consumes the
 * down/up events before [detectTapGestures]' own double-tap detector can see an
 * unconsumed second tap, so plain [toggleOriginalOnDoubleTap] never fires there.
 *
 * This detector tracks taps at the raw pointer level with
 * `requireUnconsumed = false`, toggling even when SelectionContainer has already
 * marked the events as handled. The container may still select the tapped word
 * in the process (an accepted trade-off on translated text).
 */
fun Modifier.toggleOriginalOnDoubleTapInSelection(state: TranslatedTextState): Modifier =
    if (state.canToggleOriginal) {
        pointerInput(state) {
            var firstDownTime = 0L
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                if (down.uptimeMillis - firstDownTime in 1..viewConfiguration.doubleTapTimeoutMillis) {
                    state.toggleShowingOriginal()
                    firstDownTime = 0L
                } else {
                    firstDownTime = down.uptimeMillis
                }
            }
        }
    } else {
        this
    }
