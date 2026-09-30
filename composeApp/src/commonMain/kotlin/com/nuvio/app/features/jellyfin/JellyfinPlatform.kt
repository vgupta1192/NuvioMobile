package com.nuvio.app.features.jellyfin

internal expect object JellyfinPlatform {
    /** Whether the "Jellyfin" navigation entry exists on this platform (Android only for now). */
    val navEntryEnabled: Boolean

    /**
     * Runs an HTTP call off the caller's thread. Returns null only when the platform has no HTTP
     * stack (non-Android targets before they get a real implementation).
     */
    suspend fun httpCall(method: String, url: String, headers: Map<String, String>, body: String?): JellyfinHttpResponse?

    fun loadString(key: String): String?

    fun saveString(key: String, value: String?)
}
