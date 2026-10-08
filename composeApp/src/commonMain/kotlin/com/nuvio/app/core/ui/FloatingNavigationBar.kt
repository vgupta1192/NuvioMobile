package com.nuvio.app.core.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import org.jetbrains.compose.resources.DrawableResource

internal class FloatingNavigationItem(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val drawable: DrawableResource? = null,
    val content: (@Composable (onClick: () -> Unit) -> Unit)? = null,
    // Icon-only tab: keeps the bar readable when fork features (Live TV, Jellyfin) push the
    // destination count past what fits labeled on a phone. `label` still serves as the
    // accessibility content description.
    val compact: Boolean = false,
)

// Label-collapse fraction (1 = labels visible, 0 = icons only) of the floating bar's scroll
// animation. Content tabs (profile avatar, the Extra menu) read it so their visuals track
// the labeled tabs; actuals without label animation keep the 1f default.
val LocalFloatingNavLabelFraction = compositionLocalOf { 1f }

internal expect val floatingNavigationGlowSupported: Boolean

@Composable
internal expect fun FloatingNavigationBar(
    items: List<FloatingNavigationItem>,
    modifier: Modifier = Modifier,
    scrollState: NuvioNavBarScrollState? = null,
    hazeState: HazeState? = null,
    contentPadding: PaddingValues = floatingNavigationBarPadding(),
    compactSize: Boolean = false,
    glowEnabled: Boolean = true,
)

@Composable
internal fun floatingNavigationBarPadding(): PaddingValues = PaddingValues(
    bottom = nuvioBottomNavigationBarInsets().asPaddingValues().calculateBottomPadding() +
        nuvioBottomNavigationExtraVerticalPadding + 8.dp,
)

/** Footprint the floating bar takes between the status bar and the content when docked at the
 *  top: top gap (10dp, mirrors the bar's contentPadding) + tallest track (48dp + 16dp labels)
 *  + bottom gap (8dp) + breathing room. The tab host reserves this above the content. */
internal val nuvioTopNavBarReservedHeight: Dp = 10.dp + 64.dp + 8.dp + 4.dp
