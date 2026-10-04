package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable

/**
 * AutoSync (automatic subtitle sync) settings rows, shown inside Settings → Playback.
 * The feature runs only on the Android ExoPlayer engine; other platforms render nothing.
 */
@Composable
internal expect fun AutoSyncSettingsContent(enabled: Boolean, isTablet: Boolean)
