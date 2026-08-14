package com.omersusin.pitube.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omersusin.pitube.data.translation.TranslationController
import com.omersusin.pitube.data.translation.TranslationEnginePrefs
import com.omersusin.pitube.translation.Language
import com.omersusin.pitube.translation.TranslationEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State holder for the Translation settings screen: thin delegations to the
 * shared [TranslationController] / [TranslationEnginePrefs] singletons so the
 * same provider config is seen by every translating surface in the app.
 */
@HiltViewModel
class TranslationSettingsViewModel @Inject constructor(
    private val controller: TranslationController,
    private val enginePrefs: TranslationEnginePrefs,
) : ViewModel() {

    val lastError: StateFlow<String?> = controller.lastError

    val engines: List<TranslationEngine> = controller.engines

    fun currentEngine(): TranslationEngine = controller.currentEngine()

    fun selectProvider(name: String) {
        enginePrefs.providerName = name
        controller.clearError()
    }

    fun apiKey(engine: TranslationEngine): String? = enginePrefs.getApiKey(engine)

    fun maskedApiKey(engine: TranslationEngine): String? = enginePrefs.maskedKey(engine)

    fun hasApiKey(engine: TranslationEngine): Boolean = enginePrefs.hasApiKey(engine)

    fun setApiKey(engine: TranslationEngine, key: String) {
        enginePrefs.setApiKey(engine, key)
        controller.clearError()
    }

    fun apiUrl(engine: TranslationEngine): String? = enginePrefs.getApiUrl(engine)

    fun setApiUrl(engine: TranslationEngine, url: String) {
        enginePrefs.setApiUrl(engine, url)
    }

    fun selectedModel(engine: TranslationEngine): String? = enginePrefs.getSelectedModel(engine)

    fun setSelectedModel(engine: TranslationEngine, model: String) {
        enginePrefs.setSelectedModel(engine, model)
    }

    suspend fun languagesFor(engine: TranslationEngine): List<Language> =
        controller.languagesFor(engine)

    fun testConnection(targetLanguage: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onResult(controller.testConnection(targetLanguage))
        }
    }
}