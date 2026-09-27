package com.nuvio.app.features.livetv

// Live TV is an Android-only fork feature; this target only needs the declarations to compile.
internal actual object LiveTvPlatform {
    actual val navEntryEnabled: Boolean = false

    actual suspend fun fetchGuideText(cacheKey: String, url: String, forceRefresh: Boolean, maxAgeMs: Long): String? = null

    actual fun formatClock(epochMs: Long): String {
        val minutesOfDay = (epochMs / 60_000L).mod(1_440L)
        return "${(minutesOfDay / 60).toString().padStart(2, '0')}:${(minutesOfDay % 60).toString().padStart(2, '0')}"
    }
}

internal actual object LiveTvStorage {
    private val values = mutableMapOf<String, String>()

    actual fun loadString(key: String): String? = values[key]

    actual fun saveString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}
