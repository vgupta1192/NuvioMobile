package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * "Extra" nav tab: a single bar slot for the fork features. Renders the tab visual (icon +
 * label) and, while expanded, a vertical menu above the tab with one entry per fork
 * destination — instead of spending a top-level tab on each feature.
 *
 * The caller owns the expanded state and keeps the tab marked selected while the menu is
 * open, so both bar styles highlight it like a real destination.
 */
@Composable
internal fun ExtraNavTabContent(
    selected: Boolean,
    expanded: Boolean,
    showLiveTv: Boolean,
    showJellyfin: Boolean,
    popupBelowAnchor: Boolean,
    onDismiss: () -> Unit,
    onLiveTv: () -> Unit,
    onJellyfin: () -> Unit,
    iconSize: Dp,
    showLabel: Boolean,
) {
    val tokens = MaterialTheme.nuvio
    val palette = MaterialTheme.themePalette
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Rounded.Apps,
            contentDescription = null,
            modifier = Modifier
                .size(iconSize)
                .then(if (selected) Modifier.gradientMask(palette.accentBrush()) else Modifier),
            tint = if (selected) Color.White else tokens.colors.textMuted,
        )
        if (showLabel) {
            // Track the floating bar's label collapse so the Extra tab behaves like the
            // labeled tabs when the bar shrinks on scroll.
            val labelFraction = LocalFloatingNavLabelFraction.current
            Spacer(Modifier.height(NuvioTokens.Space.s2))
            Box(
                Modifier
                    .height(NuvioTokens.Space.s14 * labelFraction)
                    .clipToBounds()
                    .alpha(labelFraction),
            ) {
                Text(
                    text = "Extra",
                    color = if (selected) tokens.colors.accent else tokens.colors.textMuted,
                    style = TextStyle(
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
    if (expanded) {
        ExtraNavMenuPopup(
            showLiveTv = showLiveTv,
            showJellyfin = showJellyfin,
            belowAnchor = popupBelowAnchor,
            onDismiss = onDismiss,
            onLiveTv = onLiveTv,
            onJellyfin = onJellyfin,
        )
    }
}

/** Vertical fork-features menu, anchored just above (or below) the Extra tab. */
@Composable
private fun ExtraNavMenuPopup(
    showLiveTv: Boolean,
    showJellyfin: Boolean,
    belowAnchor: Boolean,
    onDismiss: () -> Unit,
    onLiveTv: () -> Unit,
    onJellyfin: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val gap = with(LocalDensity.current) { NuvioTokens.Space.s10.roundToPx() }
    Popup(
        alignment = if (belowAnchor) Alignment.TopCenter else Alignment.BottomCenter,
        offset = IntOffset(0, if (belowAnchor) gap else -gap),
        properties = PopupProperties(focusable = true),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .shadow(tokens.elevation.overlay, tokens.shapes.sheet)
                .background(tokens.colors.surfaceSheet, tokens.shapes.sheet)
                .padding(vertical = NuvioTokens.Space.s4, horizontal = NuvioTokens.Space.s2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showLiveTv) {
                ExtraNavMenuEntry(icon = Icons.Rounded.LiveTv, label = "Live TV") {
                    onDismiss()
                    onLiveTv()
                }
            }
            if (showJellyfin) {
                ExtraNavMenuEntry(icon = Icons.Rounded.CollectionsBookmark, label = "Jellyfin") {
                    onDismiss()
                    onJellyfin()
                }
            }
        }
    }
}

@Composable
private fun ExtraNavMenuEntry(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(tokens.shapes.compactCard)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .widthIn(min = 84.dp)
            .padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s6),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tokens.colors.textMuted,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(NuvioTokens.Space.s3))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = NuvioTokens.Type.labelXs),
            color = tokens.colors.textMuted,
            maxLines = 1,
        )
    }
}
