package com.nuvio.app.features.jellyfin

import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape

/**
 * Jellyfin fork feature (ported like the Live TV feature): the app talks directly to the user's
 * Jellyfin server, shows its libraries in a dedicated screen, merges Jellyfin results into the
 * global Search, and plays files as direct-stream sources.
 */

/** Prefix that marks a MetaPreview id as a Jellyfin item (search results route to the Jellyfin detail screen). */
const val JELLYFIN_META_ID_PREFIX = "jf:"

data class JellyfinSession(
    val serverUrl: String,
    val serverName: String,
    val userId: String,
    val userName: String,
    val accessToken: String,
) {
    /** Part of the search request key so account/server changes invalidate cached search state. */
    val sessionKey: String get() = "$serverUrl|$userId"
}

data class JellyfinLibrary(
    val id: String,
    val name: String,
    val collectionType: String?,
)

data class JellyfinItem(
    val id: String,
    val name: String,
    val type: String,
    val overview: String? = null,
    val productionYear: Int? = null,
    val communityRating: Double? = null,
    val officialRating: String? = null,
    val runTimeTicks: Long? = null,
    val indexNumber: Int? = null,
    val parentIndexNumber: Int? = null,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val imageTag: String? = null,
    val backdropTag: String? = null,
    val container: String? = null,
    val playbackPositionTicks: Long? = null,
    val playedPercentage: Double? = null,
) {
    val isSeries: Boolean get() = type.equals("Series", ignoreCase = true)
    val isFolder: Boolean
        get() = type.equals("Folder", ignoreCase = true) ||
            type.equals("AggregateFolder", ignoreCase = true) ||
            type.equals("PlaylistFolder", ignoreCase = true)
    val isEpisode: Boolean get() = type.equals("Episode", ignoreCase = true)
    val isMovie: Boolean get() = type.equals("Movie", ignoreCase = true)

    val runTimeMinutes: Int?
        get() = runTimeTicks?.let { ticks -> (ticks / RUNTIME_TICKS_PER_MINUTE).toInt() }

    val resumePositionMs: Long
        get() = playbackPositionTicks?.div(TICKS_PER_MILLISECOND)?.takeIf { it > 0L } ?: 0L

    /** True when this item type has a playable video stream (folders / box sets do not). */
    val isPlayable: Boolean
        get() = type.equals("Movie", ignoreCase = true) ||
            type.equals("Episode", ignoreCase = true) ||
            type.equals("Video", ignoreCase = true) ||
            type.equals("MusicVideo", ignoreCase = true)

    /** Stremio-style content type used for MetaPreview mapping and watch-progress keys. */
    val stremioType: String
        get() = when {
            isSeries -> "series"
            isEpisode -> "episode"
            else -> "movie"
        }
}

data class JellyfinItemPage(
    val items: List<JellyfinItem>,
    val totalRecordCount: Int,
)

data class JellyfinHttpResponse(
    val status: Int,
    val body: String,
)

data class JellyfinUiState(
    val session: JellyfinSession? = null,
    val isLoadingSession: Boolean = false,
    val sessionError: String? = null,
    val libraries: List<JellyfinLibrary> = emptyList(),
    val hiddenLibraryIds: Set<String> = emptySet(),
    val selectedLibraryId: String? = null,
    val items: List<JellyfinItem> = emptyList(),
    val totalItemCount: Int = 0,
    val isLoadingItems: Boolean = false,
    val itemsError: String? = null,
    val searchQuery: String = "",
    val sortLatestFirst: Boolean = false,
    val selectedItemId: String? = null,
    val selectedDetail: JellyfinItem? = null,
    val seasons: List<JellyfinItem> = emptyList(),
    val selectedSeasonId: String? = null,
    val episodes: List<JellyfinItem> = emptyList(),
    val isLoadingDetail: Boolean = false,
    val detailError: String? = null,
) {
    val canLoadMore: Boolean get() = items.size < totalItemCount
}

internal const val TICKS_PER_MILLISECOND = 10_000L
private const val RUNTIME_TICKS_PER_MINUTE = 600_000_000L

internal fun JellyfinItem.toMetaPreview(posterUrl: String?, backdropUrl: String?): MetaPreview = MetaPreview(
    id = JELLYFIN_META_ID_PREFIX + id,
    type = stremioType,
    name = name,
    poster = posterUrl,
    banner = backdropUrl,
    description = overview,
    releaseInfo = productionYear?.toString(),
    posterShape = PosterShape.Poster,
)
