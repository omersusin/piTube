package com.omersusin.pitube.translation

import com.omersusin.pitube.translation.engines.apertium.ApertiumEngine
import com.omersusin.pitube.translation.engines.deepl.DeeplAuthenticatedFreeApiEngine
import com.omersusin.pitube.translation.engines.deepl.DeeplAuthenticatedPaidApiEngine
import com.omersusin.pitube.translation.engines.deepl.DeeplBrowserEngine
import com.omersusin.pitube.translation.engines.glosbe.GlosbeEngine
import com.omersusin.pitube.translation.engines.kagi.KagiEngine
import com.omersusin.pitube.translation.engines.laratranslate.LaraTranslateEngine
import com.omersusin.pitube.translation.engines.lingva.LingvaEngine
import com.omersusin.pitube.translation.engines.libretranslate.LibreTranslateEngine
import com.omersusin.pitube.translation.engines.llm.ClaudeEngine
import com.omersusin.pitube.translation.engines.llm.CustomEngine
import com.omersusin.pitube.translation.engines.llm.GeminiEngine
import com.omersusin.pitube.translation.engines.llm.MistralEngine
import com.omersusin.pitube.translation.engines.llm.OpenAIEngine
import com.omersusin.pitube.translation.engines.llm.OpenRouterEngine
import com.omersusin.pitube.translation.engines.llm.PerplexityEngine
import com.omersusin.pitube.translation.engines.llm.XAiEngine
import com.omersusin.pitube.translation.engines.mint.MinTEngine
import com.omersusin.pitube.translation.engines.mozhi.MozhiEngine
import com.omersusin.pitube.translation.engines.mymemory.MyMemoryEngine
import com.omersusin.pitube.translation.engines.onering.OneRingEngine
import com.omersusin.pitube.translation.engines.pons.PonsEngine
import com.omersusin.pitube.translation.engines.simplytranslate.SimplyTranslateEngine
import com.omersusin.pitube.translation.engines.yandex.YandexEngine

/**
 * The registry of every translation provider in the app. Order is the order
 * shown in the provider picker; the first entry is the default.
 *
 * The AI families come first (ViVi's provider list), followed by the free /
 * keyless engines ported from Translate You. The default is OpenRouter: with
 * no key configured every surface gracefully falls back to the original
 * text, and the settings screen explains how to unlock it.
 */
object TranslationEngines {

    fun getAllEngines(settingsProvider: EngineSettingsProvider): List<TranslationEngine> = listOf(
        OpenRouterEngine(settingsProvider),
        OpenAIEngine(settingsProvider),
        PerplexityEngine(settingsProvider),
        ClaudeEngine(settingsProvider),
        GeminiEngine(settingsProvider),
        XAiEngine(settingsProvider),
        MistralEngine(settingsProvider),
        DeeplAuthenticatedFreeApiEngine(settingsProvider),
        DeeplAuthenticatedPaidApiEngine(settingsProvider),
        CustomEngine(settingsProvider),
        MozhiEngine(settingsProvider),
        LibreTranslateEngine(settingsProvider),
        LingvaEngine(settingsProvider),
        DeeplBrowserEngine(settingsProvider),
        KagiEngine(settingsProvider),
        MyMemoryEngine(settingsProvider),
        YandexEngine(settingsProvider),
        SimplyTranslateEngine(settingsProvider),
        MinTEngine(settingsProvider),
        GlosbeEngine(settingsProvider),
        ApertiumEngine(settingsProvider),
        OneRingEngine(settingsProvider),
        PonsEngine(settingsProvider),
        LaraTranslateEngine(settingsProvider),
    )

    fun findByName(
        name: String?,
        settingsProvider: EngineSettingsProvider,
    ): TranslationEngine = getAllEngines(settingsProvider)
        .firstOrNull { it.name == name }
        ?: getAllEngines(settingsProvider).first()
}