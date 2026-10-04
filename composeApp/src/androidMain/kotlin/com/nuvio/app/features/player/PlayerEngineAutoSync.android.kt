package com.nuvio.app.features.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.ExtractorsFactory
import com.nuvio.app.features.player.autosync.AutoSyncAnalysisOutcome
import com.nuvio.app.features.player.autosync.AutoSyncCandidateScope
import com.nuvio.app.features.player.autosync.AutoSyncExtractorsFactory
import com.nuvio.app.features.player.autosync.AutoSyncPreferences
import com.nuvio.app.features.player.autosync.AutoSyncSyncedSubtitle
import com.nuvio.app.features.player.autosync.AutomaticSubtitleSync
import com.nuvio.app.features.player.autosync.EmbeddedSubtitleTimelineLoader
import com.nuvio.app.features.player.autosync.applyAutoSyncSidecarTimeline
import com.nuvio.app.features.player.autosync.maxAlignmentShiftMs
import com.nuvio.app.features.player.autosync.replaceAutoSyncSidecarSubtitle
import com.nuvio.app.features.streams.StreamSubtitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val AUTOSYNC_TAG = "NuvioAutoSync"

private val autoSyncToastHandler = Handler(Looper.getMainLooper())

private fun autoSyncToast(context: Context, message: String) {
    autoSyncToastHandler.post {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

private fun autoSyncFailureMessage(analysisOutcome: AutoSyncAnalysisOutcome?): String =
    when (analysisOutcome) {
        AutoSyncAnalysisOutcome.NO_SUBTITLE_TRACKS,
        AutoSyncAnalysisOutcome.NO_USABLE_REFERENCE,
        -> "Sync failed: this video has no embedded subtitles to compare with."
        AutoSyncAnalysisOutcome.SUBTITLE_UNAVAILABLE,
        null,
        -> "Sync failed: Auto Sync couldn't match this subtitle to the video."
    }

private fun isHttpUrl(url: String): Boolean =
    url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

/**
 * Wraps the Android ExoPlayer controller with the AutoSync capability (port of NuvioTV's
 * AutomaticSubtitleSync wiring). The libmpv engine and other platforms keep the plain
 * subtitle attach path.
 */
internal class AutoSyncExoPlayerController(
    private val base: PlayerEngineController,
    private val exoPlayer: ExoPlayer,
    private val sidecar: SidecarSubtitleController,
    private val scope: CoroutineScope,
    private val context: Context,
    private val sourceUrl: () -> String,
    private val sourceHeaders: () -> Map<String, String>,
    private val subtitleCandidates: () -> List<StreamSubtitle>,
    private val useLibass: () -> Boolean,
    private val resetSubtitleDelay: () -> Unit,
) : PlayerEngineController by base, AutoSyncPlayerController {

    private var autoSyncJob: Job? = null

    override fun setSubtitleUriWithAutoSync(url: String) {
        attachAndRunAutomaticSubtitleSync(url, AutoSyncCandidateScope.STARTUP_SEARCH)
    }

    override fun setSubtitleUriWithSelectedAutoSync(url: String) {
        attachAndRunAutomaticSubtitleSync(url, AutoSyncCandidateScope.SELECTED_ONLY)
    }

    /** Called when the player surface is disposed so a running analysis cannot linger. */
    fun dispose() {
        autoSyncJob?.cancel()
        autoSyncJob = null
    }

    private fun attachAndRunAutomaticSubtitleSync(
        selectedUrl: String,
        candidateScope: AutoSyncCandidateScope,
    ) {
        AutoSyncPreferences.ensureLoaded(context)
        val streamUrlAtCall = sourceUrl()
        val enabled = AutoSyncPreferences.isEnabled(context)
        if (!enabled ||
            !isHttpUrl(selectedUrl) ||
            !isHttpUrl(streamUrlAtCall) ||
            selectedUrl == streamUrlAtCall
        ) {
            base.setSubtitleUri(selectedUrl)
            return
        }
        val selectedSubtitle = subtitleCandidates().firstOrNull { it.url == selectedUrl }
        if (selectedSubtitle == null || selectedSubtitle.language.isBlank()) {
            base.setSubtitleUri(selectedUrl)
            return
        }
        if (
            candidateScope == AutoSyncCandidateScope.STARTUP_SEARCH &&
            !AutoSyncPreferences.claimStartupRun(exoPlayer.hashCode(), streamUrlAtCall)
        ) {
            // AutoSync already ran once for this stream; attach the plain way unless the
            // sidecar is already showing exactly this subtitle.
            if (sidecar.activeSidecarSubtitleKey != selectedUrl) {
                base.setSubtitleUri(selectedUrl)
            }
            return
        }
        if (!sidecar.canAttachAddonSubtitleViaSidecar(selectedUrl, useLibass())) {
            autoSyncToast(context, "Sync failed: Auto Sync doesn't support this subtitle format.")
            base.setSubtitleUri(selectedUrl)
            return
        }

        autoSyncJob?.cancel()

        val sourceUrlAtStart = streamUrlAtCall
        val sourceHeadersAtStart = sourceHeaders()
        val selectedHeaders = selectedSubtitle.headers.orEmpty()
        val candidatesAtStart = subtitleCandidates()
            .filter { it.url != streamUrlAtCall }
            .distinctBy { it.url }

        // One download feeds both the sidecar renderer and the analysis. It completes with null
        // on failure or cancellation so neither side can wait on it forever.
        val selectedBodyDeferred = CompletableDeferred<String?>()
        val started = sidecar.startSidecarAddonSubtitle(
            url = selectedUrl,
            headers = selectedHeaders,
            useLibass = useLibass(),
            rawBodyLoader = {
                selectedBodyDeferred.await()
                    ?: throw IllegalStateException("Subtitle body unavailable")
            },
        )
        if (!started) {
            autoSyncToast(
                context,
                "Sync failed: Auto Sync couldn't match this subtitle to the video.",
            )
            base.setSubtitleUri(selectedUrl)
            return
        }
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()

        autoSyncJob = scope.launch {
            launch {
                val body = try {
                    AutomaticSubtitleSync.downloadSubtitleBody(
                        url = selectedUrl,
                        headers = selectedHeaders,
                    )
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    Log.w(AUTOSYNC_TAG, "AUTO_SYNC_V2 subtitle download failed", error)
                    null
                }
                selectedBodyDeferred.complete(body)
            }
            // The user can switch to a built-in track, turn subtitles off or open another stream
            // while this runs; the fallbacks below must then leave their choice alone.
            fun stillRelevant(): Boolean =
                sourceUrl() == sourceUrlAtStart &&
                    sidecar.activeSidecarSubtitleKey == selectedUrl

            try {
                Log.d(
                    AUTOSYNC_TAG,
                    "AUTO_SYNC_V2 start scope=${candidateScope.name} " +
                        "lang=${selectedSubtitle.language} candidates=${candidatesAtStart.size}",
                )
                var analysisOutcome: AutoSyncAnalysisOutcome? = null
                val resolved = AutomaticSubtitleSync.findTimelineRetime(
                    sourceKey = sourceUrlAtStart,
                    sourceHeaders = sourceHeadersAtStart,
                    selectedSubtitleUrl = selectedUrl,
                    selectedSubtitleHeaders = selectedHeaders,
                    selectedSubtitleBodyDeferred = selectedBodyDeferred,
                    preferredLanguage = selectedSubtitle.language,
                    alternativeSubtitles = if (candidateScope == AutoSyncCandidateScope.STARTUP_SEARCH) {
                        candidatesAtStart.map { candidate ->
                            AutoSyncSubtitleCandidate(
                                url = candidate.url,
                                language = candidate.language,
                                name = candidate.name,
                            )
                        }
                    } else {
                        emptyList()
                    },
                    alternativeSubtitlesProvider = if (candidateScope == AutoSyncCandidateScope.STARTUP_SEARCH) {
                        {
                            subtitleCandidates().map { candidate ->
                                AutoSyncSubtitleCandidate(
                                    url = candidate.url,
                                    language = candidate.language,
                                    name = candidate.name,
                                )
                            }
                        }
                    } else {
                        null
                    },
                    onReferenceReady = {},
                    onAnalysisOutcome = { outcome -> analysisOutcome = outcome },
                )

                if (resolved == null) {
                    if (!stillRelevant()) return@launch
                    if (sidecar.activeSidecarSubtitleKey == null) {
                        sidecar.startSidecarAddonSubtitle(
                            url = selectedUrl,
                            headers = selectedHeaders,
                            useLibass = useLibass(),
                        )
                    }
                    autoSyncToast(context, autoSyncFailureMessage(analysisOutcome))
                    return@launch
                }

                if (sourceUrl() != sourceUrlAtStart) return@launch
                val activeSubtitleUrl = sidecar.activeSidecarSubtitleKey
                if (activeSubtitleUrl != selectedUrl && activeSubtitleUrl != resolved.subtitleUrl) {
                    return@launch
                }

                val chosenUrl = resolved.subtitleUrl
                // A confident match whose whole-film correction is within the user's tolerance
                // keeps the selected subtitle's original timing instead of retiming it.
                val toleranceMs = AutoSyncPreferences.syncToleranceMs.value
                val withinToleranceMs = toleranceMs.takeIf {
                    it > 0 && chosenUrl == selectedUrl && resolved.timeline.maxAlignmentShiftMs() <= it
                }
                val applied = when {
                    withinToleranceMs != null -> sidecar.activeSidecarSubtitleKey == selectedUrl
                    chosenUrl == selectedUrl -> applyAutoSyncSidecarTimeline(
                        sidecar = sidecar,
                        url = selectedUrl,
                        timeline = resolved.timeline,
                    )
                    sidecar.activeSidecarSubtitleKey == null &&
                        sidecar.startSidecarAddonSubtitle(
                            url = chosenUrl,
                            headers = resolved.subtitleHeaders,
                            useLibass = useLibass(),
                            rawBodyLoader = resolved.subtitleBody?.let { body ->
                                suspend { body }
                            },
                        ) -> applyAutoSyncSidecarTimeline(
                        sidecar = sidecar,
                        url = chosenUrl,
                        timeline = resolved.timeline,
                    )
                    else -> replaceAutoSyncSidecarSubtitle(
                        sidecar = sidecar,
                        expectedCurrentUrl = selectedUrl,
                        url = chosenUrl,
                        headers = resolved.subtitleHeaders,
                        rawBody = resolved.subtitleBody,
                        useLibass = useLibass(),
                        timeline = resolved.timeline,
                    )
                }

                if (!applied) {
                    if (!stillRelevant()) return@launch
                    if (sidecar.activeSidecarSubtitleKey == null) {
                        sidecar.startSidecarAddonSubtitle(
                            url = selectedUrl,
                            headers = selectedHeaders,
                            useLibass = useLibass(),
                        )
                    }
                    autoSyncToast(context, "Sync failed: Auto Sync couldn't match this subtitle to the video.")
                    return@launch
                }

                // Note: when AutoSync swaps in a better-matching candidate the runtime's
                // selected-subtitle highlight is not updated (cosmetic; the sidecar renderer
                // and the sync state are correct).
                resetSubtitleDelay()
                AutoSyncSyncedSubtitle.mark(chosenUrl)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                Log.w(AUTOSYNC_TAG, "AUTO_SYNC_V2 failed", error)
                if (!stillRelevant()) return@launch
                if (sidecar.activeSidecarSubtitleKey == null) {
                    sidecar.startSidecarAddonSubtitle(
                        url = selectedUrl,
                        headers = selectedHeaders,
                        useLibass = useLibass(),
                    )
                }
                autoSyncToast(context, "Sync failed: Auto Sync couldn't match this subtitle to the video.")
            }
        }.also { job ->
            job.invokeOnCompletion { selectedBodyDeferred.complete(null) }
        }
    }
}

/**
 * Wraps [delegate] so AutoSync can observe embedded subtitle timing while the stream opens
 * (output is forwarded unchanged), and starts the embedded index prefetch. Returns null when
 * AutoSync is disabled or the source is not a remote HTTP stream.
 */
internal fun autoSyncExtractorsFactoryOrNull(
    delegate: ExtractorsFactory,
    sourceUrl: String,
    sourceHeaders: Map<String, String>,
    scope: CoroutineScope,
    context: Context,
): ExtractorsFactory? {
    AutoSyncPreferences.ensureLoaded(context)
    if (!AutoSyncPreferences.isEnabled(context)) return null
    if (!isHttpUrl(sourceUrl)) return null
    val factory = AutoSyncExtractorsFactory(delegate = delegate, sourceKey = sourceUrl)
    EmbeddedSubtitleTimelineLoader.prefetch(scope, sourceUrl, sourceHeaders)
    return factory
}
