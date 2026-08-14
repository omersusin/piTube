package com.omersusin.pitube.ui.translation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
) {
    /** True when a real, different translation is available to display. */
    val isTranslated: Boolean
        get() = translated != null && translated.isNotBlank() && translated != original

    /** The text a REPLACE-mode surface should show. Never blank. */
    val displayText: String
        get() = translated?.takeIf { it.isNotBlank() } ?: original

    /** True when the surface should render the original below the translation. */
    val showOriginalBelow: Boolean
        get() = mode == "DUAL" && isTranslated
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

    var translated by remember(text) { mutableStateOf<String?>(null) }
    val active = masterEnabled && featureEnabled && text.isNotBlank()

    LaunchedEffect(text, targetLanguage, active) {
        translated = if (active) {
            controller.translate(text, targetLanguage.takeIf { it.isNotBlank() })
        } else {
            null
        }
    }

    return remember(text, translated, mode) {
        TranslatedTextState(text, translated?.trim(), mode)
    }
}