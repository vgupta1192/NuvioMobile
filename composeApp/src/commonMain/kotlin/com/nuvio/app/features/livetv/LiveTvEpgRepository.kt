package com.nuvio.app.features.livetv

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** XMLTV guide data (epgshare01 lists by default), matched to channels by normalized name. */
object LiveTvEpgRepository {
    val DEFAULT_SOURCES = listOf(
        EpgSource("epg_us1", "United States (US1)", "https://epgshare01.online/epgshare01/epg_ripper_US1.xml.gz"),
        EpgSource("epg_uk1", "United Kingdom (UK1)", "https://epgshare01.online/epgshare01/epg_ripper_UK1.xml.gz", isEnabled = false),
        EpgSource("epg_in1", "India (IN1)", "https://epgshare01.online/epgshare01/epg_ripper_IN1.xml.gz", isEnabled = false),
        EpgSource("epg_ca1", "Canada (CA1)", "https://epgshare01.online/epgshare01/epg_ripper_CA1.xml.gz", isEnabled = false),
        EpgSource("epg_br1", "Brasil (BR1)", "https://epgshare01.online/epgshare01/epg_ripper_BR1.xml.gz", isEnabled = false),
        EpgSource("epg_pt1", "Portugal (PT1)", "https://epgshare01.online/epgshare01/epg_ripper_PT1.xml.gz", isEnabled = false),
    )

    private const val ENABLED_SOURCES_KEY = "epg_enabled_sources"
    private const val CACHE_MAX_AGE_MS = 12L * 3_600_000L

    private val qualityTokens = setOf("hd", "fhd", "uhd", "4k", "sd", "hdtv", "fullhd", "hevc", "h265")
    private val fillerTokens = setOf("canal", "channel", "tv", "rede", "and", "e", "the", "live", "aovivo", "us", "usa", "uk")
    private val parensRegex = Regex("""[(\[][^)\]]*[)\]]""")
    private val nonAlnumRegex = Regex("""[^a-z0-9]+""")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(LiveTvEpgUiState())
    val uiState: StateFlow<LiveTvEpgUiState> = _uiState.asStateFlow()

    private val lock = SynchronizedObject()
    private var programsByChannelId: Map<String, List<EpgProgram>> = emptyMap()
    private var keyToChannelId: Map<String, String> = emptyMap()
    private val matchCache = mutableMapOf<String, String?>()

    private var syncJob: Job? = null
    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true
        val saved = LiveTvStorage.loadString(ENABLED_SOURCES_KEY)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.toSet()
        if (saved != null) {
            _uiState.update { state ->
                state.copy(sources = DEFAULT_SOURCES.map { it.copy(isEnabled = it.id in saved) })
            }
        }
        sync(forceRefresh = false)
    }

    fun toggleSource(sourceId: String) {
        _uiState.update { state ->
            state.copy(sources = state.sources.map { if (it.id == sourceId) it.copy(isEnabled = !it.isEnabled) else it })
        }
        LiveTvStorage.saveString(
            ENABLED_SOURCES_KEY,
            _uiState.value.sources.filter { it.isEnabled }.joinToString(",") { it.id },
        )
        sync(forceRefresh = false)
    }

    fun sync(forceRefresh: Boolean) {
        syncJob?.cancel()
        syncJob = scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val active = _uiState.value.sources.filter { it.isEnabled }
            val now = nowEpochMs()
            val programs = mutableMapOf<String, List<EpgProgram>>()
            val keys = mutableMapOf<String, String>()
            var failures = 0
            for (source in active) {
                try {
                    val xml = LiveTvPlatform.fetchGuideText(source.id, source.url, forceRefresh, CACHE_MAX_AGE_MS)
                    if (xml.isNullOrBlank()) {
                        failures++
                        continue
                    }
                    val parsed = XmlTvParser.parse(xml, now)
                    for ((id, channel) in parsed.channels) {
                        (listOf(id.substringBefore('.')) + channel.displayNames)
                            .map(::normalize)
                            .filter { it.isNotBlank() }
                            .forEach { key -> if (key !in keys) keys[key] = id } // Kotlin/Native-safe putIfAbsent (iOS build)
                    }
                    programs.putAll(parsed.programsByChannelId)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    failures++
                }
            }
            synchronized(lock) {
                programsByChannelId = programs
                keyToChannelId = keys
                matchCache.clear()
            }
            val total = programs.values.sumOf { it.size }
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    totalPrograms = total,
                    errorMessage = when {
                        active.isEmpty() -> null
                        total == 0 && failures > 0 -> "Could not load the programme guide"
                        else -> null
                    },
                    revision = state.revision + 1,
                )
            }
        }
    }

    fun epgFor(channel: LiveTvChannel, nowMs: Long = nowEpochMs()): ChannelEpgInfo? {
        val (xmlTvId, programs) = synchronized(lock) {
            val key = channel.stableKey()
            val id = if (matchCache.containsKey(key)) {
                matchCache[key]
            } else {
                matchChannelId(channel.name).also { matchCache[key] = it }
            }
            id to id?.let { programsByChannelId[it] }.orEmpty()
        }
        if (xmlTvId == null || programs.isEmpty()) return null
        val now = programs.firstOrNull { it.isLive(nowMs) }
        val next = programs.firstOrNull { it.startEpochMs >= (now?.endEpochMs ?: nowMs) }
        return ChannelEpgInfo(xmlTvChannelId = xmlTvId, nowProgram = now, nextProgram = next, programs = programs)
    }

    private fun matchChannelId(channelName: String): String? {
        val norm = normalize(channelName)
        if (norm.isBlank()) return null
        keyToChannelId[norm]?.let { return it }
        if (norm.length < 4) return null
        return keyToChannelId.entries.firstOrNull { (key, _) ->
            key.length >= 4 && (norm.startsWith(key) || key.startsWith(norm))
        }?.value
    }

    private fun normalize(text: String): String =
        parensRegex.replace(text.lowercase(), " ")
            .split(nonAlnumRegex)
            .filter { it.isNotBlank() && it !in qualityTokens && it !in fillerTokens }
            .joinToString("")

    internal fun nowEpochMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
}
