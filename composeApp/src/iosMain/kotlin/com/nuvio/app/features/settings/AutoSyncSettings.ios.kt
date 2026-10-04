package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable

@Composable
internal actual fun AutoSyncSettingsContent(enabled: Boolean, isTablet: Boolean) {
    // AutoSync is an Android (ExoPlayer) feature; no settings rows on iOS.
}
