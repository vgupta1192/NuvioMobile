package com.nuvio.app.features.livetv

import com.nuvio.app.features.streams.StreamItem
import kotlin.math.max

/**
 * Live TV (fork feature, ported from NuvioTV's livetv branch): channels are the catalog items of
 * the user's installed addons, grouped into categories, with XMLTV programme guide data matched
 * by channel name.
 */
data class LiveTvChannel(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val logo: String? = null,
    val genres: List<String> = emptyList(),
    val description: String? = null,
    val addonName: String,
    val manifestUrl: String,
    val addonLogo: String? = null,
    val catalogId: String,
    val catalogName: String,
) {
    val displayLogo: String?
        get() = logo?.takeIf { it.isNotBlank() } ?: poster?.takeIf { it.isNotBlank() }

    val primaryGenre: String?
        get() = genres.firstOrNull { it.isNotBlank() }

    fun stableKey(): String = "${manifestUrl.trim().removeSuffix("/")}:$type:$id"
}

data class LiveTvAddonOption(
    val manifestUrl: String,
    val name: String,
    val logoUrl: String? = null,
    val catalogCount: Int = 0,
    val isSelected: Boolean = false,
)

data class LiveTvStreamOption(
    val label: String,
    val subtitle: String?,
    val url: String,
    val stream: StreamItem,
)

data class LiveTvUiState(
    val allChannels: List<LiveTvChannel> = emptyList(),
    val filteredChannels: List<LiveTvChannel> = emptyList(),
    val availableAddons: List<LiveTvAddonOption> = emptyList(),
    val selectedAddonUrls: Set<String> = emptySet(),
    val favoriteKeys: Set<String> = emptySet(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String = LIVE_TV_CATEGORY_ALL,
    val searchQuery: String = "",
    val isLoadingChannels: Boolean = false,
    val channelsErrorMessage: String? = null,
    val selectedChannel: LiveTvChannel? = null,
    val streams: List<LiveTvStreamOption> = emptyList(),
    val isLoadingStreams: Boolean = false,
    val streamsErrorMessage: String? = null,
    val hideAdultChannels: Boolean = true,
) {
    fun isFavorite(channel: LiveTvChannel): Boolean = favoriteKeys.contains(channel.stableKey())
}

const val LIVE_TV_CATEGORY_ALL = "__all__"
const val LIVE_TV_CATEGORY_FAVORITES = "__favorites__"

data class EpgProgram(
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val category: String? = null,
) {
    fun isLive(nowMs: Long): Boolean = nowMs in startEpochMs until endEpochMs

    fun progress(nowMs: Long): Float {
        if (nowMs <= startEpochMs) return 0f
        if (nowMs >= endEpochMs) return 1f
        val duration = endEpochMs - startEpochMs
        if (duration <= 0L) return 0f
        return ((nowMs - startEpochMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    fun remainingMinutes(nowMs: Long): Int =
        if (nowMs >= endEpochMs) 0 else max(0, ((endEpochMs - nowMs) / 60_000L).toInt())

    val timeRange: String
        get() = "${LiveTvPlatform.formatClock(startEpochMs)} - ${LiveTvPlatform.formatClock(endEpochMs)}"
}

data class ChannelEpgInfo(
    val xmlTvChannelId: String,
    val nowProgram: EpgProgram? = null,
    val nextProgram: EpgProgram? = null,
    val programs: List<EpgProgram> = emptyList(),
)

data class EpgSource(
    val id: String,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
)

data class LiveTvEpgUiState(
    val isLoading: Boolean = false,
    val totalPrograms: Int = 0,
    val errorMessage: String? = null,
    val sources: List<EpgSource> = LiveTvEpgRepository.DEFAULT_SOURCES,
    /** Bumped after every sync so the UI re-reads guide data. */
    val revision: Int = 0,
)

/**
 * Picks a URL a player can open for a live channel: HLS, MPEG-TS, DASH, RTMP/RTSP, plain HTTP
 * media, from url / externalUrl / sources, unwrapping Android intent: and vlc:// style wrappers
 * IPTV addons use. Torrents and magnets are skipped.
 */
fun resolvePlayableLiveTvUrl(stream: StreamItem): String? {
    val candidates = listOfNotNull(stream.url, stream.externalUrl) + stream.sources
    for (candidate in candidates) {
        val cleaned = cleanLiveTvStreamUrl(candidate) ?: continue
        if (isSupportedLiveTvProtocol(cleaned) && !isTorrentOrMagnet(cleaned)) return cleaned
    }
    return null
}

fun cleanLiveTvStreamUrl(rawUrl: String?): String? {
    if (rawUrl.isNullOrBlank()) return null
    var url = rawUrl.trim()
    if (url.startsWith("intent:", ignoreCase = true)) {
        val candidate = url.substring(7).substringBefore('#').substringBefore(';')
        if (candidate.startsWith("http://", ignoreCase = true) || candidate.startsWith("https://", ignoreCase = true)) {
            url = candidate
        }
    }
    for (scheme in listOf("vlc://", "mxplayer://", "wuffy://", "nplayer://", "iplayer://")) {
        if (url.startsWith(scheme, ignoreCase = true)) {
            url = url.substring(scheme.length)
            break
        }
    }
    return url.takeIf { it.isNotBlank() }
}

fun isSupportedLiveTvProtocol(url: String): Boolean {
    val u = url.lowercase().trim()
    return listOf(
        "http://", "https://", "rtmp://", "rtmps://", "rtmpe://", "rtmpt://",
        "rtsp://", "rtsps://", "udp://", "rtp://", "mms://", "mmsh://",
    ).any { u.startsWith(it) }
}

private fun isTorrentOrMagnet(url: String): Boolean {
    val u = url.lowercase().trim()
    return u.startsWith("magnet:") || u.startsWith("torrent:")
}

object AdultChannelDetector {
    private val brands = setOf(
        "playboy", "sexy hot", "sexyhot", "venus", "sextreme", "hustler", "brazzers", "dorcel",
        "penthouse", "private tv", "private spice", "vivid", "redlight", "babes tv", "reality kings",
        "evil angel", "x-mo", "exotica", "man-x", "centoxcento", "pink o", "passion xxx",
        "the adult channel", "adult channel", "dusk tv", "extasy tv", "satisfaction channel",
        "daring tv", "frenchlover", "bang bros", "bangbros", "naughty america", "mofos", "onlyfans",
        "hardx", "vixen", "blacked", "tushy", "sex prive", "sex privé", "canal adulto", "erox",
        "hot pleasures", "adultzone", "taboo", "milf", "fetish",
    )
    private val keywords = setOf(
        "adult", "adulto", "adultos", "adulte", "erotico", "erótico", "eroticos", "eróticos",
        "erotica", "erótica", "erotic", "erotika", "porno", "pornô", "porn", "pornografia", "nsfw",
        "hardcore", "softcore", "hentai", "xxx", "xxxx", "18+", "+18",
    )
    private val whitelist = setOf("adult swim", "adultswim", "the adults")
    private val wordSplit = Regex("[\\s\\[\\](){}\\-_/,.:;+*|#?!]+")

    fun isAdult(channel: LiveTvChannel): Boolean {
        val name = channel.name.lowercase().trim()
        if (isWhitelisted(name)) return false
        if (channel.genres.any(::isAdultText)) return true
        if (isAdultText(channel.catalogName) || isAdultText(channel.catalogId)) return true
        if (isAdultText(name)) return true
        return channel.description?.let { isAdultText(it.take(120)) } == true
    }

    fun isAdultCategory(category: String): Boolean =
        !isWhitelisted(category.lowercase()) && isAdultText(category)

    private fun isWhitelisted(text: String): Boolean = whitelist.any { text.contains(it) }

    private fun isAdultText(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val clean = raw.lowercase().trim()
        if (isWhitelisted(clean)) return false
        if (clean.split(wordSplit).any { it in keywords }) return true
        if (listOf("18+", "+18", "🔞", "xxx", "porn", "erótic", "erotic", "adulto", "adults").any { clean.contains(it) }) {
            return true
        }
        return brands.any { clean.contains(it) }
    }
}
