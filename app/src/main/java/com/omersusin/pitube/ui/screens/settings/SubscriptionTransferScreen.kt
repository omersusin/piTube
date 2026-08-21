package com.omersusin.pitube.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.SubscriptionRepository
import com.omersusin.pitube.data.local.SubscriptionTransfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionTransferScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val raw = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            ?: return@runCatching null
                        SubscriptionTransfer.parse(raw)
                    }.getOrNull()
                }
                val imported = result?.let { parsed ->
                    SubscriptionTransfer.apply(context, parsed.channels)
                } ?: 0
                busy = false
                Toast.makeText(
                    context,
                    if (result == null) {
                        context.getString(R.string.subscription_transfer_import_failed)
                    } else {
                        context.getString(
                            R.string.subscription_transfer_imported_template,
                            imported,
                            result.format,
                        )
                    },
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val subscriptions = SubscriptionTransfer.collectExportChannels(context)
                        val content = SubscriptionTransfer.buildNewPipeJson(subscriptions)
                        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                    }
                }
            }
        }

    val exportOpmlLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val subscriptions = SubscriptionTransfer.collectExportChannels(context)
                        val content = SubscriptionTransfer.buildOpml(subscriptions)
                        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                    }
                }
            }
        }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                    }
                    Text(
                        text = stringResource(R.string.settings_item_subscription_transfer),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { SectionHeader(text = stringResource(R.string.subscription_transfer_import_header)) }
            item {
                SettingsGroup {
                    TransferRow(
                        icon = Icons.Outlined.FileUpload,
                        title = stringResource(R.string.subscription_transfer_import),
                        subtitle = stringResource(R.string.subscription_transfer_import_subtitle),
                        enabled = !busy,
                        onClick = {
                            importLauncher.launch(arrayOf("*/*"))
                        },
                    )
                }
            }
            item { SectionHeader(text = stringResource(R.string.subscription_transfer_export_header)) }
            item {
                SettingsGroup {
                    TransferRow(
                        icon = Icons.Outlined.FileDownload,
                        title = stringResource(R.string.subscription_transfer_export_newpipe),
                        subtitle = stringResource(R.string.subscription_transfer_export_newpipe_subtitle),
                        enabled = !busy,
                        onClick = {
                            exportLauncher.launch("pitube_subscriptions.json")
                        },
                    )
                    HorizontalDivider(
                        Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                    TransferRow(
                        icon = Icons.Outlined.SwapHoriz,
                        title = stringResource(R.string.subscription_transfer_export_opml),
                        subtitle = stringResource(R.string.subscription_transfer_export_opml_subtitle),
                        enabled = !busy,
                        onClick = {
                            exportOpmlLauncher.launch("pitube_subscriptions.opml")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        enabled = enabled,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
