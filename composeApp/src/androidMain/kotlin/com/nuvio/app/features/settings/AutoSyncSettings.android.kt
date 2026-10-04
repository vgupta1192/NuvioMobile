package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.player.autosync.AutoSyncPreferences

@Composable
internal actual fun AutoSyncSettingsContent(enabled: Boolean, isTablet: Boolean) {
    val context = LocalContext.current
    AutoSyncPreferences.ensureLoaded(context)
    val checked by AutoSyncPreferences.enabled.collectAsStateWithLifecycle()

    SettingsSwitchRow(
        title = "Auto Sync Subtitles",
        description = "Match the selected add-on subtitle to the timing of the subtitles " +
            "embedded in the video.",
        checked = checked,
        enabled = enabled,
        isTablet = isTablet,
        onCheckedChange = { AutoSyncPreferences.setEnabled(context, it) },
    )

    // Tolerance and search depth only matter while AutoSync is on.
    if (checked) {
        val toleranceMs by AutoSyncPreferences.syncToleranceMs.collectAsStateWithLifecycle()
        val thorough by AutoSyncPreferences.aggressiveMode.collectAsStateWithLifecycle()

        SettingsGroupDivider(isTablet = isTablet)
        SettingsNavigationRow(
            title = "Auto Sync Tolerance",
            description = if (toleranceMs > 0) {
                "Keep the original timing when the correction needed is $toleranceMs ms or " +
                    "smaller. Tap to change."
            } else {
                "Keep the original timing unless any correction is needed. Tap to change."
            },
            enabled = enabled,
            isTablet = isTablet,
            onClick = {
                val options = AutoSyncPreferences.syncToleranceOptionsMs
                val next = options[(options.indexOf(toleranceMs) + 1).mod(options.size)]
                AutoSyncPreferences.setSyncToleranceMs(context, next)
            },
        )
        SettingsGroupDivider(isTablet = isTablet)
        SettingsSwitchRow(
            title = "Thorough Auto Sync Search",
            description = "Keep comparing other subtitles and embedded tracks for a closer " +
                "match before syncing. When off, the first strong match is used sooner. " +
                "Only confident matches are ever applied.",
            checked = thorough,
            enabled = enabled,
            isTablet = isTablet,
            onCheckedChange = { AutoSyncPreferences.setAggressiveMode(context, it) },
        )
    }
}
