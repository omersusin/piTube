package com.omersusin.pitube.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.CommonLanguages
import com.omersusin.pitube.translation.Language
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The dedicated Translation settings screen: provider picker (ViVi's radio
 * list), per-provider API key / model / custom endpoint, test action, target
 * language, display mode and the "what to translate" toggles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: TranslationSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferences = remember { PlayerPreferences(context) }

    val translationEnabled by preferences.translationEnabled.collectAsState(initial = false)
    val translateTitles by preferences.translateTitles.collectAsState(initial = false)
    val translateCaptions by preferences.translateCaptions.collectAsState(initial = false)
    val translateDescriptions by preferences.translateDescriptions.collectAsState(initial = false)
    val translateComments by preferences.translateComments.collectAsState(initial = false)
    val translateChannelNames by preferences.translateChannelNames.collectAsState(initial = false)
    val translatePlaylistTitles by preferences.translatePlaylistTitles.collectAsState(initial = false)
    val doubleTapOriginal by preferences.translationDoubleTapOriginal.collectAsState(initial = true)
    val targetLanguage by preferences.translationTargetLanguage.collectAsState(initial = "")
    val displayMode by preferences.translationMode.collectAsState(initial = "REPLACE")

    val engine = viewModel.currentEngine()
    val providerKeyMissing = engine.apiKeyState == ApiKeyState.REQUIRED && !viewModel.hasApiKey(engine)

    // Dialog state
    var showProviderDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showCustomModelDialog by remember { mutableStateOf(false) }
    var showBaseUrlDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showModeDialog by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var apiKeyInput by remember { mutableStateOf(viewModel.apiKey(engine).orEmpty()) }
    var customModelInput by remember { mutableStateOf("") }
    var baseUrlInput by remember { mutableStateOf(viewModel.apiUrl(engine).orEmpty()) }

    var languages by remember { mutableStateOf<List<Language>?>(null) }
    LaunchedEffect(engine.name) {
        languages = viewModel.languagesFor(engine)
    }

    val selectedModel = viewModel.selectedModel(engine)
    val modelInList = selectedModel != null && engine.supportedModels.contains(selectedModel)

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                    }
                    Text(
                        text = stringResource(R.string.translation_settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------------- Provider ----------------
            item { SectionHeader(text = stringResource(R.string.translation_settings_header_provider)) }

            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.Translate,
                        title = stringResource(R.string.translation_provider_row),
                        subtitle = stringResource(
                            R.string.translation_provider_row_subtitle,
                            engine.name,
                        ),
                        onClick = { showProviderDialog = true },
                    )
                    val engineStatusNote = engine.statusNote
                    if (engineStatusNote != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                text = engineStatusNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    HorizontalDivider(
                        Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Key,
                        title = stringResource(R.string.translation_api_key_row),
                        subtitle = when {
                            viewModel.maskedApiKey(engine) != null ->
                                viewModel.maskedApiKey(engine)!!
                            engine.apiKeyState == ApiKeyState.REQUIRED ->
                                stringResource(R.string.translation_api_key_required_hint)
                            else -> stringResource(R.string.translation_api_key_not_set)
                        },
                        onClick = {
                            apiKeyInput = viewModel.apiKey(engine).orEmpty()
                            showApiKeyDialog = true
                        },
                    )
                    if (engine.supportedModels.isNotEmpty()) {
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Public,
                            title = stringResource(R.string.translation_model_row),
                            subtitle = if (selectedModel.isNullOrBlank()) {
                                stringResource(R.string.translation_model_not_selected)
                            } else {
                                selectedModel
                            },
                            onClick = { showModelDialog = true },
                        )
                    }
                    if (engine.urlModifiable) {
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Public,
                            title = stringResource(R.string.translation_base_url_row),
                            subtitle = viewModel.apiUrl(engine)
                                ?: stringResource(R.string.translation_base_url_default),
                            onClick = {
                                baseUrlInput = viewModel.apiUrl(engine).orEmpty()
                                showBaseUrlDialog = true
                            },
                        )
                    }
                    HorizontalDivider(
                        Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Science,
                        title = stringResource(R.string.translation_test_row),
                        subtitle = stringResource(R.string.translation_test_row_subtitle),
                        onClick = {
                            testResult = null
                            viewModel.testConnection(targetLanguage) { result ->
                                testResult = result.fold(
                                    onSuccess = {
                                        Pair(true, it)
                                    },
                                    onFailure = {
                                        Pair(false, it.message ?: "")
                                    },
                                )
                            }
                        },
                    )
                }
            }

            // ---------------- Language & display ----------------
            item { SectionHeader(text = stringResource(R.string.translation_settings_header_language)) }

            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.Language,
                        title = stringResource(R.string.translation_target_language_row),
                        subtitle = if (targetLanguage.isBlank()) {
                            stringResource(
                                R.string.translation_target_language_device,
                                CommonLanguages.displayNameOf(Locale.getDefault().language),
                            )
                        } else {
                            CommonLanguages.displayNameOf(targetLanguage)
                        },
                        onClick = { showLanguageDialog = true },
                    )
                    HorizontalDivider(
                        Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Translate,
                        title = stringResource(R.string.translation_mode_row),
                        subtitle = stringResource(
                            if (displayMode == "DUAL") {
                                R.string.translation_mode_dual
                            } else {
                                R.string.translation_mode_replace
                            },
                        ),
                        onClick = { showModeDialog = true },
                    )
                }
            }

            // ---------------- What to translate ----------------
            item { SectionHeader(text = stringResource(R.string.translation_settings_header_content)) }

            if (providerKeyMissing) {
                item {
                    Text(
                        text = stringResource(R.string.translation_missing_key_hint, engine.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }

            item {
                SettingsGroup {
                    TranslationToggleRow(
                        title = stringResource(R.string.translation_toggle_master),
                        checked = translationEnabled,
                        onCheckedChange = {
                            coroutineScope.launch { preferences.setTranslationEnabled(it) }
                        },
                    )
                    TranslationToggleRow(
                        title = stringResource(R.string.translation_toggle_titles),
                        checked = translateTitles,
                        onCheckedChange = {
                            coroutineScope.launch { preferences.setTranslateTitles(it) }
                        },
                    )
                    TranslationToggleRow(
                        title = stringResource(R.string.translation_toggle_captions),
                        subtitle = stringResource(R.string.translation_toggle_captions_subtitle),
                        checked = translateCaptions,
                        onCheckedChange = {
                            coroutineScope.launch { preferences.setTranslateCaptions(it) }
                        },
                    )
                    TranslationToggleRow(
                        title = stringResource(R.string.translation_toggle_descriptions),
                        checked = translateDescriptions,
                        onCheckedChange = {
                            coroutineScope.launch { preferences.setTranslateDescriptions(it) }
                        },
                    )
                    TranslationToggleRow(
                        title = stringResource(R.string.translation_toggle_comments),
                        checked = translateComments,
                        onCheckedChange = {
                            coroutineScope.launch { preferences.setTranslateComments(it) }
                        },
                    )
                    TranslationToggleRow(
                        title = stringResource(R.string.translation_toggle_channel_names),
                        checked = translateChannelNames,
                        onCheckedChange = {
                            coroutineScope.launch { preferences.setTranslateChannelNames(it) }
                        },
                    )
                    TranslationToggleRow(
                        title = stringResource(R.string.translation_toggle_playlist_titles),
                        checked = translatePlaylistTitles,
                        onCheckedChange = {
                            coroutineScope.launch { preferences.setTranslatePlaylistTitles(it) }
                        },
                    )
                    TranslationToggleRow(
                        title = stringResource(R.string.translation_toggle_double_tap_original),
                        subtitle = stringResource(R.string.translation_toggle_double_tap_original_subtitle),
                        checked = doubleTapOriginal,
                        onCheckedChange = {
                            coroutineScope.launch { preferences.setTranslationDoubleTapOriginal(it) }
                        },
                    )
                    if (translateCaptions) {
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = stringResource(R.string.translation_caption_note),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // ---------------- Dialogs ----------------

    if (showProviderDialog) {
        RadioListDialog(
            title = stringResource(R.string.translation_provider_row),
            options = viewModel.engines.map { it.name },
            selected = engine.name,
            onSelect = {
                viewModel.selectProvider(it)
                showProviderDialog = false
            },
            onDismiss = { showProviderDialog = false },
            optionNotes = viewModel.engines.map { it.statusNote },
        )
    }

    if (showApiKeyDialog) {
        TextInputDialog(
            title = stringResource(R.string.translation_api_key_row),
            value = apiKeyInput,
            password = true,
            onValueChange = { apiKeyInput = it },
            onConfirm = {
                viewModel.setApiKey(engine, apiKeyInput)
                showApiKeyDialog = false
            },
            onDismiss = { showApiKeyDialog = false },
        )
    }

    if (showModelDialog) {
        val modelOptions = engine.supportedModels + listOf("custom_input")
        RadioListDialog(
            title = stringResource(R.string.translation_model_row),
            options = modelOptions,
            selected = if (modelInList) selectedModel else "custom_input",
            onSelect = { choice ->
                if (choice == "custom_input") {
                    customModelInput = selectedModel.orEmpty()
                    showCustomModelDialog = true
                } else {
                    viewModel.setSelectedModel(engine, choice)
                }
                showModelDialog = false
            },
            onDismiss = { showModelDialog = false },
        )
    }

    if (showCustomModelDialog) {
        TextInputDialog(
            title = stringResource(R.string.translation_model_custom),
            value = customModelInput,
            onValueChange = { customModelInput = it },
            onConfirm = {
                viewModel.setSelectedModel(engine, customModelInput)
                showCustomModelDialog = false
            },
            onDismiss = { showCustomModelDialog = false },
        )
    }

    if (showBaseUrlDialog) {
        TextInputDialog(
            title = stringResource(R.string.translation_base_url_dialog_title),
            value = baseUrlInput,
            onValueChange = { baseUrlInput = it },
            onConfirm = {
                viewModel.setApiUrl(engine, baseUrlInput)
                showBaseUrlDialog = false
            },
            onDismiss = { showBaseUrlDialog = false },
        )
    }

    if (showLanguageDialog) {
        val deviceLabel = stringResource(R.string.translation_target_language_device_label)
        val languageList = remember(languages, engine.name, deviceLabel) {
            listOf(Language("", deviceLabel)) + (languages ?: CommonLanguages.languages)
        }
        RadioListDialog(
            title = stringResource(R.string.translation_target_language_row),
            options = languageList.map { it.code },
            optionLabels = languageList.map { it.name },
            selected = targetLanguage,
            onSelect = { code ->
                coroutineScope.launch { preferences.setTranslationTargetLanguage(code) }
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showModeDialog) {
        RadioListDialog(
            title = stringResource(R.string.translation_mode_row),
            options = listOf("REPLACE", "DUAL"),
            optionLabels = listOf(
                stringResource(R.string.translation_mode_replace),
                stringResource(R.string.translation_mode_dual),
            ),
            selected = displayMode,
            onSelect = { mode ->
                coroutineScope.launch { preferences.setTranslationMode(mode) }
                showModeDialog = false
            },
            onDismiss = { showModeDialog = false },
        )
    }

    testResult?.let { (success, message) ->
        AlertDialog(
            onDismissRequest = { testResult = null },
            title = {
                Text(
                    if (success) {
                        stringResource(R.string.translation_test_success)
                    } else {
                        stringResource(R.string.translation_test_failure)
                    },
                )
            },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { testResult = null }) {
                    Text(stringResource(R.string.btn_ok))
                }
            },
        )
    }
}

@Composable
private fun TranslationToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RadioListDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    optionLabels: List<String>? = null,
    optionNotes: List<String?>? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                itemsIndexed(options) { index, option ->
                    val label = optionLabels?.getOrNull(index) ?: option
                    val note = optionNotes?.getOrNull(index)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = label, style = MaterialTheme.typography.bodyMedium)
                            if (note != null) {
                                Text(
                                    text = note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    password: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                visualTransformation = if (password) {
                    PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.btn_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
    )
}