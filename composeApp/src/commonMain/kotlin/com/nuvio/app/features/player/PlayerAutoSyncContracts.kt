package com.nuvio.app.features.player

/**
 * Optional AutoSync (automatic subtitle sync) capability layered beside PlayerEngineController.
 * Implemented only by engines that can run the analysis (Android ExoPlayer); other platforms
 * keep the plain subtitle attach path. Mirrors NuvioTV's AutoSyncPlayerController contract.
 */
interface AutoSyncPlayerController {
    /** Attach [url] as an external subtitle and run AutoSync in startup scope (once per stream). */
    fun setSubtitleUriWithAutoSync(url: String)

    /** Attach [url] as an external subtitle and always run AutoSync for exactly this subtitle. */
    fun setSubtitleUriWithSelectedAutoSync(url: String)
}

/** One downloadable add-on subtitle AutoSync may analyse or swap in as a better match. */
data class AutoSyncSubtitleCandidate(
    val url: String,
    val language: String,
    val name: String? = null,
)
