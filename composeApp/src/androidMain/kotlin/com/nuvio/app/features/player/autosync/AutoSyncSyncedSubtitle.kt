package com.nuvio.app.features.player.autosync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The add-on subtitle whose on-screen timing AutoSync corrected or confirmed. Cleared whenever
 * the sidecar starts or stops a subtitle, since that renders the original timing again.
 * (The "Auto synced" chip UI is TV-only; Mobile keeps the state for future use.)
 */
internal object AutoSyncSyncedSubtitle {
    private val _url = MutableStateFlow<String?>(null)
    val url: StateFlow<String?> = _url.asStateFlow()

    fun mark(subtitleUrl: String) {
        _url.value = subtitleUrl
    }

    fun clear() {
        _url.value = null
    }
}
