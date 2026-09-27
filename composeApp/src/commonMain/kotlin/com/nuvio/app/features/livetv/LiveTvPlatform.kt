package com.nuvio.app.features.livetv

internal expect object LiveTvPlatform {
    /** Whether the main tab bar shows a Live TV entry on this platform. */
    val navEntryEnabled: Boolean

    /**
     * Downloads an XMLTV guide (plain or gzip, detected by magic bytes), cached on disk for
     * [maxAgeMs]. Returns null when it cannot be fetched and no cached copy exists.
     */
    suspend fun fetchGuideText(cacheKey: String, url: String, forceRefresh: Boolean, maxAgeMs: Long): String?

    /** Local wall-clock time as HH:mm. */
    fun formatClock(epochMs: Long): String
}

internal expect object LiveTvStorage {
    fun loadString(key: String): String?
    fun saveString(key: String, value: String?)
}
