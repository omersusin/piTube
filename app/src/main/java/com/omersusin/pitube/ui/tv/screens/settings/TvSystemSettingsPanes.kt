package com.omersusin.pitube.ui.tv.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.AppUiModePreferences
import com.omersusin.pitube.platform.AppUiMode
import com.omersusin.pitube.ui.tv.components.TvSelectionRow
import kotlinx.coroutines.launch

@Composable
fun TvInterfaceSettingsPane(
    modePreferences: AppUiModePreferences,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val selectedMode by modePreferences.mode.collectAsStateWithLifecycle(initialValue = AppUiMode.AUTOMATIC)

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppUiMode.entries.forEach { mode ->
            val label = when (mode) {
                AppUiMode.AUTOMATIC -> stringResource(R.string.interface_mode_automatic)
                AppUiMode.MOBILE -> stringResource(R.string.interface_mode_mobile)
                AppUiMode.TV -> stringResource(R.string.interface_mode_tv)
            }
            val summary = when (mode) {
                AppUiMode.AUTOMATIC -> stringResource(R.string.interface_mode_automatic_summary)
                AppUiMode.MOBILE -> stringResource(R.string.interface_mode_mobile_summary)
                AppUiMode.TV -> stringResource(R.string.interface_mode_tv_summary)
            }
            TvSelectionRow(
                label = label,
                supportingText = summary,
                selected = selectedMode == mode,
                onClick = {
                    if (mode != selectedMode) {
                        scope.launch {
                            // Release focus before the whole UI tree is swapped out.
                            focusManager.clearFocus(force = true)
                            withFrameNanos { }
                            modePreferences.setMode(mode)
                        }
                    }
                },
            )
        }
    }
}
