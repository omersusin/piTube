package com.omersusin.pitube.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

import androidx.compose.ui.res.vectorResource
import com.omersusin.pitube.R

private data class NavItemSpec(
    val index: Int,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
    val labelRes: Int
)

/**
 * The bottom bar is a fixed 5-slot layout:
 *
 *     [reorderable] [reorderable] [Search - larger] [reorderable] [Profile]
 *
 * The search slot is always present, enlarged and centered — it is the only
 * search entry point in piTube and is not part of the reorder customization.
 * The reorderable slots are Home/Shorts/Library (per the user's nav order and
 * visibility prefs). Categories is no longer a bar destination and the "You"
 * tab is a fixed slot at the end that never navigates, so it is never part of
 * the reorderable set or the default-start-tab resolution.
 */

@Composable
fun FloatingBottomNavBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isHomeEnabled: Boolean = true,
    isShortsEnabled: Boolean = true,
    navOrder: List<Int> = listOf(0, 1, 4),
    onAccountClick: () -> Unit = {},
    isAccountSelected: Boolean = false,
    accountAvatarUrl: String? = null,
    accountExpired: Boolean = false,
) {
    val shortsIcon = ImageVector.vectorResource(id = R.drawable.ic_shorts)

    val reorderableItems = remember(isHomeEnabled, isShortsEnabled, navOrder) {
        val items = buildList {
            if (isHomeEnabled)   add(NavItemSpec(0, Icons.Filled.Home,         Icons.Outlined.Home,         R.string.nav_home))
            if (isShortsEnabled) add(NavItemSpec(1, shortsIcon,                shortsIcon,                    R.string.nav_shorts))
            add(NavItemSpec(4, Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary, R.string.nav_library))
        }
        val order = navOrder.withIndex().associate { it.value to it.index }
        items.sortedBy { order[it.index] ?: Int.MAX_VALUE }
    }
    val searchItem = NavItemSpec(5, Icons.Filled.Search, Icons.Outlined.Search, R.string.nav_search)

    // Split the reorderable set around the fixed center search slot so the
    // search icon always sits in the middle of the bar.
    val leftCount = (reorderableItems.size + 1) / 2
    val leftItems = reorderableItems.take(leftCount)
    val rightItems = reorderableItems.drop(leftCount)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            leftItems.forEach { spec ->
                BottomNavItem(
                    modifier = Modifier.weight(1f),
                    icon = if (selectedIndex == spec.index) spec.filledIcon else spec.outlinedIcon,
                    label = stringResource(spec.labelRes),
                    selected = selectedIndex == spec.index,
                    onClick = { onItemSelected(spec.index) }
                )
            }

            // Fixed, always-visible enlarged center search slot.
            BottomNavItem(
                modifier = Modifier.weight(1f),
                icon = if (selectedIndex == searchItem.index) searchItem.filledIcon else searchItem.outlinedIcon,
                label = stringResource(searchItem.labelRes),
                selected = selectedIndex == searchItem.index,
                enlarged = true,
                onClick = { onItemSelected(searchItem.index) }
            )

            rightItems.forEach { spec ->
                BottomNavItem(
                    modifier = Modifier.weight(1f),
                    icon = if (selectedIndex == spec.index) spec.filledIcon else spec.outlinedIcon,
                    label = stringResource(spec.labelRes),
                    selected = selectedIndex == spec.index,
                    onClick = { onItemSelected(spec.index) }
                )
            }

            // Fixed "You" tab, always last.
            AccountBottomNavItem(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.nav_you),
                selected = isAccountSelected,
                avatarUrl = accountAvatarUrl,
                expired = accountExpired,
                onClick = onAccountClick
            )
        }
    }
}

/**
 * The "You" tab: a circular avatar when a YouTube account is active (with an
 * error ring when its session has expired), falling back to the generic
 * account icon when signed out.
 */
@Composable
private fun AccountBottomNavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    expired: Boolean = false,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "accountScale"
    )

    val iconTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "accountTint"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scale)
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, radius = 28.dp),
                    onClick = onClick
                )
                .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .then(if (expired) Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.error) else Modifier)
                    .padding(if (expired) 1.dp else 0.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    coil3.compose.AsyncImage(
                        model = avatarUrl,
                        contentDescription = label,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = if (selected) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle,
                        contentDescription = label,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enlarged: Boolean = false,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    val iconTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "iconTint"
    )
    
    val iconSize = if (enlarged) 36.dp else 22.dp
    val interactionSource = remember { MutableInteractionSource() }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scale)
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, radius = 28.dp),
                    onClick = onClick
                )
                .padding(horizontal = if (enlarged) 8.dp else 12.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
