package com.omersusin.pitube.ui.screens.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.R

@Composable
fun ShareGroupSheet(onCopyLink: () -> Unit, onCopyAtTime: () -> Unit, onShare: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.share), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
            SheetRow(Icons.Outlined.Share, stringResource(R.string.share)) { onShare(); onDismiss() }
            SheetRow(Icons.Outlined.Link, stringResource(R.string.player_action_copy_link)) { onCopyLink(); onDismiss() }
            SheetRow(Icons.Outlined.Timer, stringResource(R.string.player_action_copy_link_at_time)) { onCopyAtTime(); onDismiss() }
        }
    }
}

@Composable
private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(16.dp)); Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
