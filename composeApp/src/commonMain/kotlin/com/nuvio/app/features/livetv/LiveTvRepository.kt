package com.nuvio.app.features.livetv

import com.nuvio.app.features.addons.AddonCatalog
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.buildAddonResourceUrl
import com.nuvio.app.features.addons.fetchAddonResponseText
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.streams.StreamParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object LiveTvRepository {
    private const val SELECTED_ADDONS_KEY = "selected_addons"
    private const val FAVORITES_KEY = "favorite_channels"
    private const val HIDE_ADULT_KEY = "hide_adult"
    private const val MAX_CATALOG_PAGES = 15

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    private var initialized = false
    private var loadJob: Job? = null
    private var streamJob: Job? = null
    private var enabledAddons: List<ManagedAddon> = emptyList()
    private val streamCache = mutableMapOf<String, List<LiveTvStreamOption>>()

    fun initialize() {
        if (initialized) return
        initialized = true
        AddonRepository.initialize()
        LiveTvEpgRepository.initialize()
        _uiState.update {
            it.copy(
                favoriteKeys = loadSet(FAVORITES_KEY).orEmpty(),
                hideAdultChannels = LiveTvStorage.loadString(HIDE_ADULT_KEY)?.toBooleanStrictOrNull() ?: true,
            )
        }
        scope.launch {
            AddonRepository.uiState
                .map { state -> state.addons.filter { it.isActive } }
                .distinctUntilChanged { old, new ->
                    old.map { it.manifestUrl to it.manifest?.version } == new.map { it.manifestUrl to it.manifest?.version }
                }
                .collect { addons ->
                    enabledAddons = addons
                    onAddonsChanged(forceReload = false)
                }
        }
    }

    fun refresh() {
        streamCache.clear()
        LiveTvEpgRepository.sync(forceRefresh = true)
        onAddonsChanged(forceReload = true)
    }

    private fun onAddonsChanged(forceReload: Boolean) {
        val saved = loadSet(SELECTED_ADDONS_KEY)
        // Only addons that have live TV catalogs; movie/series addons never show here
        val options = enabledAddons.map { addon ->
            LiveTvAddonOption(
                manifestUrl = addon.manifestUrl,
                name = addon.displayTitle,
                logoUrl = addon.manifest?.logoUrl,
                catalogCount = addon.tvCatalogs().size,
            )
        }.filter { it.catalogCount > 0 }
        val selected = saved?.filterTo(mutableSetOf()) { url -> options.any { it.manifestUrl == url } }
            ?: options.mapTo(mutableSetOf()) { it.manifestUrl }
        _uiState.update { state ->
            state.copy(
                availableAddons = options.map { it.copy(isSelected = it.manifestUrl in selected) },
                selectedAddonUrls = selected,
            )
        }
        loadChannels(selected, forceReload)
    }

    fun toggleAddon(manifestUrl: String) {
        val current = _uiState.value.selectedAddonUrls
        val updated = if (manifestUrl in current) current - manifestUrl else current + manifestUrl
        saveSet(SELECTED_ADDONS_KEY, updated)
        _uiState.update { state ->
            state.copy(
                selectedAddonUrls = updated,
                availableAddons = state.availableAddons.map { it.copy(isSelected = it.manifestUrl in updated) },
            )
        }
        loadChannels(updated, forceReload = false)
    }

    private fun loadChannels(selectedUrls: Set<String>, forceReload: Boolean) {
        loadJob?.cancel()
        val addons = enabledAddons.filter { it.manifestUrl in selectedUrls }
        if (addons.isEmpty()) {
            _uiState.update { it.copy(allChannels = emptyList(), isLoadingChannels = false, channelsErrorMessage = null).refiltered() }
            return
        }
        _uiState.update { it.copy(isLoadingChannels = true, channelsErrorMessage = null) }
        loadJob = scope.launch {
            val gate = Semaphore(6)
            val results = coroutineScope {
                addons.flatMap { addon -> addon.tvCatalogs().map { addon to it } }
                    .map { (addon, catalog) ->
                        async {
                            gate.withPermit {
                                val items = try {
                                    fetchWholeCatalog(addon.manifestUrl, catalog, forceReload)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Throwable) {
                                    emptyList()
                                }
                                Triple(addon, catalog, items)
                            }
                        }
                    }
                    .awaitAll()
            }
            val seen = mutableSetOf<String>()
            val channels = buildList {
                for ((addon, catalog, items) in results) {
                    val catalogName = catalog.name.ifBlank { catalog.id }
                    for (meta in items) {
                        if (meta.id.isBlank() || meta.name.isBlank()) continue
                        val genres = meta.genres.filter { it.isNotBlank() }.ifEmpty {
                            if (catalogName.isNotBlank() && !catalogName.equals("tv", ignoreCase = true)) listOf(catalogName) else emptyList()
                        }
                        val channel = LiveTvChannel(
                            id = meta.id,
                            type = meta.type.ifBlank { catalog.type },
                            name = meta.name,
                            poster = meta.poster,
                            logo = meta.logo ?: meta.poster,
                            genres = genres,
                            description = meta.description,
                            addonName = addon.displayTitle,
                            manifestUrl = addon.manifestUrl,
                            addonLogo = addon.manifest?.logoUrl,
                            catalogId = catalog.id,
                            catalogName = catalogName,
                        )
                        if (seen.add(channel.stableKey())) add(channel)
                    }
                }
            }
            _uiState.update { state ->
                state.copy(
                    allChannels = channels,
                    isLoadingChannels = false,
                    channelsErrorMessage = if (channels.isEmpty()) "No channels found in the selected addons" else null,
                ).refiltered()
            }
        }
    }

    private suspend fun fetchWholeCatalog(manifestUrl: String, catalog: AddonCatalog, forceRefresh: Boolean): List<MetaPreview> {
        val items = mutableListOf<MetaPreview>()
        val ids = mutableSetOf<String>()
        var skip: Int? = null
        repeat(MAX_CATALOG_PAGES) {
            val page = fetchCatalogPage(
                manifestUrl = manifestUrl,
                type = catalog.type,
                catalogId = catalog.id,
                skip = skip,
                forceRefresh = forceRefresh,
            )
            val added = page.items.count { ids.add(it.id) && items.add(it) }
            val supportsSkip = catalog.extra.any { it.name == "skip" }
            if (added == 0 || !supportsSkip || page.nextSkip == null) return items
            skip = page.nextSkip
        }
        return items
    }

    fun setCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category).refiltered() }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query).refiltered() }
    }

    fun setHideAdultChannels(hide: Boolean) {
        LiveTvStorage.saveString(HIDE_ADULT_KEY, hide.toString())
        _uiState.update { it.copy(hideAdultChannels = hide).refiltered() }
    }

    fun toggleFavorite(channel: LiveTvChannel) {
        val key = channel.stableKey()
        val current = _uiState.value.favoriteKeys
        val updated = if (key in current) current - key else current + key
        saveSet(FAVORITES_KEY, updated)
        _uiState.update { state ->
            val category = if (updated.isEmpty() && state.selectedCategory == LIVE_TV_CATEGORY_FAVORITES) {
                LIVE_TV_CATEGORY_ALL
            } else {
                state.selectedCategory
            }
            state.copy(favoriteKeys = updated, selectedCategory = category).refiltered()
        }
    }

    fun selectChannel(channel: LiveTvChannel?) {
        streamJob?.cancel()
        if (channel == null) {
            _uiState.update { it.copy(selectedChannel = null, streams = emptyList(), isLoadingStreams = false, streamsErrorMessage = null) }
            return
        }
        val cached = streamCache[channel.stableKey()]
        _uiState.update {
            it.copy(
                selectedChannel = channel,
                streams = cached.orEmpty(),
                isLoadingStreams = cached == null,
                streamsErrorMessage = null,
            )
        }
        if (cached != null) return
        streamJob = scope.launch {
            val result = try {
                Result.success(fetchStreams(channel))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
            val streams = result.getOrNull().orEmpty()
            if (result.isSuccess) streamCache[channel.stableKey()] = streams
            _uiState.update { state ->
                if (state.selectedChannel?.stableKey() != channel.stableKey()) return@update state
                state.copy(
                    streams = streams,
                    isLoadingStreams = false,
                    streamsErrorMessage = when {
                        result.isFailure -> result.exceptionOrNull()?.message ?: "Could not load streams for this channel"
                        streams.isEmpty() -> "No playable stream found for this channel"
                        else -> null
                    },
                )
            }
        }
    }

    /** Channel next to the selected one in the current list ([offset] = +1 next, -1 previous). */
    fun adjacentChannel(offset: Int): LiveTvChannel? {
        val state = _uiState.value
        val list = state.filteredChannels
        if (list.isEmpty()) return null
        val index = list.indexOfFirst { it.stableKey() == state.selectedChannel?.stableKey() }
        if (index == -1) return list.first()
        return list[(index + offset).mod(list.size)]
    }

    private suspend fun fetchStreams(channel: LiveTvChannel): List<LiveTvStreamOption> {
        val addon = enabledAddons.firstOrNull { it.manifestUrl == channel.manifestUrl }
        val manifest = addon?.manifest
        val payload = fetchAddonResponseText(
            url = buildAddonResourceUrl(
                manifestUrl = channel.manifestUrl,
                resource = "stream",
                type = channel.type,
                id = channel.id,
            ),
        )
        return StreamParser.parse(
            payload = payload,
            addonName = addon?.displayTitle ?: channel.addonName,
            addonId = manifest?.id ?: channel.manifestUrl,
            addonLogo = manifest?.logoUrl,
        ).mapNotNull { stream ->
            val url = resolvePlayableLiveTvUrl(stream) ?: return@mapNotNull null
            LiveTvStreamOption(
                label = stream.name?.takeIf { it.isNotBlank() } ?: stream.title?.takeIf { it.isNotBlank() } ?: channel.name,
                subtitle = (stream.description ?: stream.title)?.takeIf { it.isNotBlank() && it != stream.name },
                url = url,
                stream = stream,
            )
        }.distinctBy { it.url }
    }

    private fun LiveTvUiState.refiltered(): LiveTvUiState {
        val visible = if (hideAdultChannels) allChannels.filterNot(AdultChannelDetector::isAdult) else allChannels
        val categories = visible
            .flatMap { it.genres + it.catalogName }
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("tv", ignoreCase = true) && (!hideAdultChannels || !AdultChannelDetector.isAdultCategory(it)) }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
        val category = when {
            selectedCategory == LIVE_TV_CATEGORY_ALL || selectedCategory == LIVE_TV_CATEGORY_FAVORITES -> selectedCategory
            categories.any { it.equals(selectedCategory, ignoreCase = true) } -> selectedCategory
            else -> LIVE_TV_CATEGORY_ALL
        }
        val query = searchQuery.trim().lowercase()
        val filtered = visible.filter { channel ->
            val inCategory = when (category) {
                LIVE_TV_CATEGORY_ALL -> true
                LIVE_TV_CATEGORY_FAVORITES -> channel.stableKey() in favoriteKeys
                else -> channel.catalogName.equals(category, ignoreCase = true) ||
                    channel.genres.any { it.equals(category, ignoreCase = true) }
            }
            inCategory && (
                query.isEmpty() ||
                    channel.name.lowercase().contains(query) ||
                    channel.catalogName.lowercase().contains(query) ||
                    channel.genres.any { it.lowercase().contains(query) }
                )
        }.sortedByDescending { it.stableKey() in favoriteKeys }
        return copy(categories = categories, selectedCategory = category, filteredChannels = filtered)
    }

    private fun ManagedAddon.tvCatalogs(): List<AddonCatalog> {
        val catalogs = manifest?.catalogs.orEmpty()
            .filter { catalog -> catalog.extra.none { it.isRequired } }
        return catalogs.filter { LiveTvCatalogFilter.isLiveTvCatalog(this, it) }
    }

    private fun loadSet(key: String): Set<String>? =
        LiveTvStorage.loadString(key)?.split('\n')?.filter { it.isNotBlank() }?.toSet()

    private fun saveSet(key: String, values: Set<String>) {
        LiveTvStorage.saveString(key, values.joinToString("\n"))
    }
}
