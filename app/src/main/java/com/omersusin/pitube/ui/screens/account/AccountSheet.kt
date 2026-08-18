package com.omersusin.pitube.ui.screens.account

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.AccountSwitcher
import com.omersusin.pitube.data.local.ProfileManager
import com.omersusin.pitube.ui.components.rememberFlowSheetState

/**
 * The "You" sheet, opened from the bottom navigation's profile tab.
 *
 * Two jobs in one popup, mirroring the account menus of the reference clients:
 *
 * 1. **Who you are.** The active profile's identity, with a sign-in entry
 *    point when running signed out, and a one-tap management surface to
 *    switch / add / remove the stored accounts.
 * 2. **Where your stuff lives.** Shortcuts to the library destinations that
 *    belong to the account - history, playlists, likes, watch later, saved
 *    shorts and downloads - plus settings and the about page.
 *
 * Profile management is shared with [AccountSwitcherSheet] through
 * [ProfileManagementSection], so both entry points stay behaviourally
 * identical.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
    onAddYouTubeAccount: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenLikedVideos: () -> Unit,
    onOpenWatchLater: () -> Unit,
    onOpenSavedShorts: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val switcher = remember(context) { AccountSwitcher(context) }
    val profiles by switcher.profiles.collectAsState()
    val activeId by switcher.activeProfileId.collectAsState()
    val active = profiles.firstOrNull { it.id == activeId } ?: switcher.active()
    val sheetState = rememberFlowSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Signed-out only: the roster lists just the Add rows when no
            // account is stored, so the sign-in entry point lives here as the
            // first item of the Profiles section. Once an account is active it
            // is represented by its Profiles row, never by a separate card.
            if (active.isLocal && active.name == ProfileManager.DEFAULT_LOCAL_NAME) {
                SignedOutPrompt(
                    onSignIn = {
                        onDismiss()
                        onSignIn()
                    }
                )
            }

            Text(
                text = stringResource(R.string.account_sheet_profiles_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )

            ProfileManagementSection(
                switcher = switcher,
                onAddYouTubeAccount = {
                    onDismiss()
                    onAddYouTubeAccount()
                },
                onProfileChanged = onDismiss
            )

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.library),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )

            QuickLinkRow(
                icon = Icons.Outlined.History,
                label = stringResource(R.string.library_history_label),
                onClick = {
                    onDismiss()
                    onOpenHistory()
                }
            )
            QuickLinkRow(
                icon = Icons.AutoMirrored.Outlined.PlaylistPlay,
                label = stringResource(R.string.library_playlists_label),
                onClick = {
                    onDismiss()
                    onOpenPlaylists()
                }
            )
            QuickLinkRow(
                icon = Icons.Outlined.ThumbUp,
                label = stringResource(R.string.library_liked_videos_label),
                onClick = {
                    onDismiss()
                    onOpenLikedVideos()
                }
            )
            QuickLinkRow(
                icon = Icons.Outlined.Schedule,
                label = stringResource(R.string.library_watch_later_label),
                onClick = {
                    onDismiss()
                    onOpenWatchLater()
                }
            )
            QuickLinkRow(
                icon = Icons.Outlined.BookmarkBorder,
                label = stringResource(R.string.library_saved_shorts_label),
                onClick = {
                    onDismiss()
                    onOpenSavedShorts()
                }
            )
            QuickLinkRow(
                icon = Icons.Outlined.Download,
                label = stringResource(R.string.library_downloads_label),
                onClick = {
                    onDismiss()
                    onOpenDownloads()
                }
            )

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.sb_settings_general_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )

            QuickLinkRow(
                icon = Icons.Outlined.Settings,
                label = stringResource(R.string.settings),
                onClick = {
                    onDismiss()
                    onOpenSettings()
                }
            )
            QuickLinkRow(
                icon = Icons.Outlined.Info,
                label = stringResource(R.string.tab_about),
                onClick = {
                    onDismiss()
                    onOpenAbout()
                }
            )
        }
    }
}

/**
 * The sign-in prompt shown when the app is running with no account (the
 * always-present default local profile).
 *
 * Shown only as the first item of the Profiles section while signed out. When
 * a YouTube account is active it is represented by its own ProfileManagement
 * row (avatar, name, handle, active checkmark, delete) - never also by this
 * card, so an account is never rendered twice in this sheet.
 */
@Composable
private fun SignedOutPrompt(
    onSignIn: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.PersonOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.account_sheet_signed_out_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.account_sheet_signed_out_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Button(onClick = onSignIn) {
            Text(stringResource(R.string.account_sheet_sign_in_cta))
        }
    }
}

/**
 * A single shortcut row in the sheet - icon, label, chevron. Tapping it runs
 * [onClick], which the caller pairs with a destination navigation.
 */
@Composable
internal fun QuickLinkRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "quickLinkScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}