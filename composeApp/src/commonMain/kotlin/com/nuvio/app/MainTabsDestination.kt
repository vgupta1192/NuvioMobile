package com.nuvio.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.ExtraNavTabContent
import com.nuvio.app.core.ui.LocalNuvioBottomNavigationOverlayPadding
import com.nuvio.app.core.ui.LocalNuvioNavBarScrollState
import com.nuvio.app.core.ui.LocalNuvioTopNavBarActive
import com.nuvio.app.core.ui.NuvioNavBarScrollState
import com.nuvio.app.core.ui.NuvioClassicNavigationBar
import com.nuvio.app.core.ui.FloatingNavigationBar
import com.nuvio.app.core.ui.FloatingNavigationItem
import com.nuvio.app.core.ui.floatingNavigationBarPadding
import com.nuvio.app.core.ui.nuvioTopNavBarReservedHeight
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.rememberNuvioNavBarScrollState
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileSwitcherTab
import com.nuvio.app.features.settings.NavBarPosition
import com.nuvio.app.features.settings.NavBarStyle
import com.nuvio.app.features.settings.ThemeSettingsRepository
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_nav_home
import nuvio.composeapp.generated.resources.compose_nav_library
import nuvio.composeapp.generated.resources.compose_nav_profile
import nuvio.composeapp.generated.resources.compose_nav_search
import nuvio.composeapp.generated.resources.sidebar_library
import nuvio.composeapp.generated.resources.sidebar_search
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MainTabsDestination(
    selectedTab: AppScreenTab,
    initialHomeReady: Boolean,
    rootRouteActive: Boolean,
    useTabletFloatingTabBar: Boolean,
    useNativeNavigation: Boolean,
    useNativeTabBar: Boolean,
    liquidGlassNativeTabBarSupported: Boolean,
    liquidGlassNativeTabBarEnabled: Boolean,
    requests: AppTabRequests,
    state: AppTabState,
    actions: (isTabletLayout: Boolean) -> AppTabActions,
    onBack: () -> Unit,
    onTabSelected: (AppScreenTab) -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
) {
    PlatformBackHandler(enabled = rootRouteActive, onBack = onBack)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletLayout = useTabletFloatingTabBar || maxWidth >= 768.dp
        val tabActions = remember(actions, isTabletLayout) { actions(isTabletLayout) }
        val useNativeBottomTabs = if (useNativeNavigation) {
            useNativeTabBar
        } else {
            liquidGlassNativeTabBarSupported && liquidGlassNativeTabBarEnabled && initialHomeReady
        }
        val tabsRouteActive = rootRouteActive
        val navBarScrollState = rememberNuvioNavBarScrollState()
        val navBarHazeState = rememberHazeState()
        // Fork features share one "Extra" tab (vertical menu with Live TV + Jellyfin) instead
        // of one compact tab each, so the bar stays readable on phones. The tab counts as
        // selected while its menu is open, which parks the jelly pill on it until it closes.
        val extraLiveTvEnabled = com.nuvio.app.features.livetv.LiveTvPlatform.navEntryEnabled
        val extraJellyfinEnabled = com.nuvio.app.features.jellyfin.JellyfinPlatform.navEntryEnabled
        var extraMenuOpen by remember { mutableStateOf(false) }
        val navBarStyleSetting by remember { ThemeSettingsRepository.navBarStyle }.collectAsStateWithLifecycle()
        val navBarPositionSetting by remember { ThemeSettingsRepository.navBarPosition }.collectAsStateWithLifecycle()
        // Fork: the floating panel can live at the top of the phone screen (Settings →
        // navigation bar → Position). Tablets already run a top bar, and the classic bar
        // is a solid bottom bar by design, so only the phone pill bar honors the setting.
        val navBarAtTop = !isTabletLayout &&
            !useNativeBottomTabs &&
            navBarStyleSetting != NavBarStyle.CLASSIC &&
            navBarPositionSetting == NavBarPosition.TOP
        val navBarGlowEnabled by ThemeSettingsRepository.navBarGlowEnabled.collectAsStateWithLifecycle()
        val floatingNavigationItems = listOfNotNull(
            FloatingNavigationItem(
                selected = selectedTab == AppScreenTab.Home,
                onClick = { onTabSelected(AppScreenTab.Home) },
                icon = Icons.Filled.Home,
                label = stringResource(Res.string.compose_nav_home),
            ),
            FloatingNavigationItem(
                selected = selectedTab == AppScreenTab.Search,
                onClick = { onTabSelected(AppScreenTab.Search) },
                drawable = Res.drawable.sidebar_search,
                label = stringResource(Res.string.compose_nav_search),
            ),
            FloatingNavigationItem(
                selected = selectedTab == AppScreenTab.Library,
                onClick = { onTabSelected(AppScreenTab.Library) },
                drawable = Res.drawable.sidebar_library,
                label = stringResource(Res.string.compose_nav_library),
            ),
            // Fork features hub: opens a vertical menu with the fork destinations instead of
            // navigating itself. `content` hosts both the tab visual and the popup.
            if (extraLiveTvEnabled || extraJellyfinEnabled) {
                FloatingNavigationItem(
                    selected = extraMenuOpen,
                    onClick = { extraMenuOpen = true },
                    label = "Extra",
                    content = { _ ->
                        // The bar's JellyTabRow renders item.label for every non-compact tab
                        // (that is where the Profile label comes from too), so the content
                        // visual must stay icon-only or the tab shows "Extra" twice.
                        ExtraNavTabContent(
                            selected = extraMenuOpen,
                            expanded = extraMenuOpen,
                            showLiveTv = extraLiveTvEnabled,
                            showJellyfin = extraJellyfinEnabled,
                            popupBelowAnchor = isTabletLayout || navBarAtTop,
                            onDismiss = { extraMenuOpen = false },
                            onLiveTv = { com.nuvio.app.features.livetv.LiveTvLauncher.open() },
                            onJellyfin = { com.nuvio.app.features.jellyfin.JellyfinLauncher.open() },
                            iconSize = 28.dp,
                            showLabel = false,
                        )
                    },
                )
            } else {
                null
            },
            FloatingNavigationItem(
                selected = selectedTab == AppScreenTab.Settings,
                onClick = { onTabSelected(AppScreenTab.Settings) },
                label = stringResource(Res.string.compose_nav_profile),
                content = { onClick ->
                    ProfileSwitcherTab(
                        selected = selectedTab == AppScreenTab.Settings,
                        onClick = onClick,
                        onProfileSelected = onProfileSelected,
                        onAddProfileRequested = onAddProfileRequested,
                        hazeState = navBarHazeState,
                        popupBelowAnchor = isTabletLayout || navBarAtTop,
                    )
                },
            ),
        )

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (initialHomeReady) 1f else 0f),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (!isTabletLayout && !useNativeBottomTabs && navBarStyleSetting == NavBarStyle.CLASSIC) {
                    NuvioClassicNavigationBar {
                        NavItem(
                            selected = selectedTab == AppScreenTab.Home,
                            onClick = { onTabSelected(AppScreenTab.Home) },
                            icon = Icons.Filled.Home,
                            contentDescription = stringResource(Res.string.compose_nav_home),
                        )
                        NavItem(
                            selected = selectedTab == AppScreenTab.Search,
                            onClick = { onTabSelected(AppScreenTab.Search) },
                            icon = Res.drawable.sidebar_search,
                            contentDescription = stringResource(Res.string.compose_nav_search),
                        )
                        NavItem(
                            selected = selectedTab == AppScreenTab.Library,
                            onClick = { onTabSelected(AppScreenTab.Library) },
                            icon = Res.drawable.sidebar_library,
                            contentDescription = stringResource(Res.string.compose_nav_library),
                        )
                        // Fork features hub (same Extra menu as the floating bar; the classic
                        // bar shows no labels).
                        if (extraLiveTvEnabled || extraJellyfinEnabled) {
                            NavItem(
                                selected = extraMenuOpen,
                                onClick = { extraMenuOpen = true },
                                content = {
                                    ExtraNavTabContent(
                                        selected = extraMenuOpen,
                                        expanded = extraMenuOpen,
                                        showLiveTv = extraLiveTvEnabled,
                                        showJellyfin = extraJellyfinEnabled,
                                        popupBelowAnchor = false,
                                        onDismiss = { extraMenuOpen = false },
                                        onLiveTv = { com.nuvio.app.features.livetv.LiveTvLauncher.open() },
                                        onJellyfin = { com.nuvio.app.features.jellyfin.JellyfinLauncher.open() },
                                        iconSize = MaterialTheme.nuvio.components.navIconSize,
                                        showLabel = false,
                                    )
                                },
                            )
                        }
                        NavItem(
                            selected = selectedTab == AppScreenTab.Settings,
                            onClick = { onTabSelected(AppScreenTab.Settings) },
                        ) {
                            ProfileSwitcherTab(
                                selected = selectedTab == AppScreenTab.Settings,
                                onClick = { onTabSelected(AppScreenTab.Settings) },
                                onProfileSelected = onProfileSelected,
                                onAddProfileRequested = onAddProfileRequested,
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                // Fork: a top-docked bar floats over the screen titles, so reserve its whole
                // footprint above the content instead. Screens see LocalNuvioTopNavBarActive
                // and drop their own status-bar padding (no double gap under the bar).
                val topNavBarClearance = if (navBarAtTop) {
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
                        nuvioTopNavBarReservedHeight
                } else {
                    0.dp
                }
                CompositionLocalProvider(
                    // The overlay padding keeps scrollable content clear of the floating bar.
                    // Top position frees the bottom edge (content clears the system gesture
                    // inset via plain padding below); the top edge is reserved explicitly.
                    LocalNuvioBottomNavigationOverlayPadding provides if (useNativeBottomTabs) {
                        49.dp
                    } else if (navBarAtTop || (isTabletLayout || navBarStyleSetting == NavBarStyle.CLASSIC)) {
                        0.dp
                    } else {
                        72.dp
                    },
                    LocalNuvioNavBarScrollState provides navBarScrollState,
                    LocalNuvioTopNavBarActive provides (topNavBarClearance > 0.dp),
                ) {
                    AppTabHost(
                        selectedTab = selectedTab,
                        requests = requests,
                        state = state,
                        actions = tabActions,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (isTabletLayout || navBarStyleSetting != NavBarStyle.CLASSIC) Modifier.hazeSource(state = navBarHazeState) else Modifier)
                            .then(if (navBarStyleSetting == NavBarStyle.ADAPTIVE) Modifier.nestedScroll(navBarScrollState.nestedScrollConnection) else Modifier)
                            .then(
                                if (navBarAtTop) {
                                    Modifier
                                        .padding(top = topNavBarClearance)
                                        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                                } else {
                                    Modifier
                                },
                            )
                            .padding(innerPadding),
                    )
                }

                if (isTabletLayout && !useNativeBottomTabs) {
                    val tabletNavBarScrollState = remember { NuvioNavBarScrollState().apply { collapse() } }
                    FloatingNavigationBar(
                        modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 416.dp),
                        scrollState = tabletNavBarScrollState,
                        hazeState = navBarHazeState,
                        contentPadding = PaddingValues(
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp,
                            bottom = 8.dp,
                        ),
                        compactSize = true,
                        items = floatingNavigationItems,
                        glowEnabled = navBarGlowEnabled,
                    )
                }

                if (!isTabletLayout && !useNativeBottomTabs && navBarStyleSetting != NavBarStyle.CLASSIC) {
                    when (navBarStyleSetting) {
                        NavBarStyle.EXPANDED -> navBarScrollState.expand()
                        NavBarStyle.COMPACT -> navBarScrollState.collapse()
                        else -> {}
                    }
                    FloatingNavigationBar(
                        modifier = Modifier.align(if (navBarAtTop) Alignment.TopCenter else Alignment.BottomCenter),
                        scrollState = navBarScrollState,
                        hazeState = navBarHazeState,
                        // The default padding reserves the bottom system inset; a top bar
                        // mirrors the tablet padding instead (status bar + gap).
                        contentPadding = if (navBarAtTop) {
                            PaddingValues(
                                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp,
                                bottom = 8.dp,
                            )
                        } else {
                            floatingNavigationBarPadding()
                        },
                        items = floatingNavigationItems,
                        glowEnabled = navBarGlowEnabled,
                    )
                }
            }
        }
    }
}
