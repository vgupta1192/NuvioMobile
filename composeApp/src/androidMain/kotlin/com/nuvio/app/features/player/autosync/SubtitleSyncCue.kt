package com.nuvio.app.features.player.autosync

/**
 * Plain-timing subtitle cue used by the AutoSync analysis pipeline. Ported from NuvioTV's
 * PlayerUiState.SubtitleSyncCue so the engine stays identical between platforms.
 */
data class SubtitleSyncCue(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)
