package com.nuvio.app.features.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.nuvio.app.core.ui.PlatformBackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioSectionLabel
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.delay

@Composable
fun LiveTvScreen(
    onBack: () -> Unit,
    onPlay: (LiveTvChannel, LiveTvStreamOption) -> Unit,
) {
    LaunchedEffect(Unit) { LiveTvRepository.initialize() }
    val state by LiveTvRepository.uiState.collectAsStateWithLifecycle()
    val epgState by LiveTvEpgRepository.uiState.collectAsStateWithLifecycle()
    var nowMs by remember { mutableLongStateOf(LiveTvEpgRepository.nowEpochMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMs = LiveTvEpgRepository.nowEpochMs()
        }
    }
    val tokens = MaterialTheme.nuvio

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.colors.background),
    ) {
        // Phones get one pane at a time; tablets keep the sidebar / list / detail layout.
        val compact = maxWidth < 840.dp
        var showFilters by rememberSaveable { mutableStateOf(false) }
        val selected = state.selectedChannel
        val compactDetailOpen = compact && selected != null
        PlatformBackHandler(enabled = compact && (showFilters || selected != null)) {
            if (showFilters) showFilters = false else LiveTvRepository.selectChannel(null)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = tokens.spacing.screenHorizontal),
        ) {
            NuvioScreenHeader(
                title = if (compactDetailOpen) selected?.name ?: "Live TV" else if (compact && showFilters) "Filters" else "Live TV",
                onBack = {
                    when {
                        compact && showFilters -> showFilters = false
                        compactDetailOpen -> LiveTvRepository.selectChannel(null)
                        else -> onBack()
                    }
                },
                actions = {
                    if (state.isLoadingChannels || epgState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    if (compact && !compactDetailOpen) {
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(Icons.Rounded.Tune, contentDescription = "Filters", tint = tokens.colors.textPrimary)
                        }
                    }
                    IconButton(onClick = LiveTvRepository::refresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = tokens.colors.textPrimary)
                    }
                },
            )
            if (compact) {
                when {
                    showFilters -> LiveTvSidebar(
                        state = state,
                        epgState = epgState,
                        modifier = Modifier.fillMaxSize().padding(bottom = 16.dp),
                    )
                    selected != null -> LiveTvDetailPanel(
                        channel = selected,
                        state = state,
                        epg = remember(selected, epgState.revision, nowMs) { LiveTvEpgRepository.epgFor(selected, nowMs) },
                        nowMs = nowMs,
                        onPlay = onPlay,
                        modifier = Modifier.fillMaxSize().padding(bottom = 16.dp),
                    )
                    else -> Column(modifier = Modifier.fillMaxSize().padding(bottom = 8.dp)) {
                        LiveTvSearchField(state)
                        Spacer(Modifier.height(8.dp))
                        LiveTvCategoryChips(state)
                        Spacer(Modifier.height(8.dp))
                        LiveTvChannelList(
                            state = state,
                            epgRevision = epgState.revision,
                            nowMs = nowMs,
                            onPlay = onPlay,
                            emptyHint = "Tap the filter icon above and select an addon with TV channels",
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)) {
                    LiveTvSidebar(
                        state = state,
                        epgState = epgState,
                        modifier = Modifier.width(250.dp).fillMaxHeight(),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        LiveTvSearchField(state)
                        Spacer(Modifier.height(12.dp))
                        LiveTvChannelList(
                            state = state,
                            epgRevision = epgState.revision,
                            nowMs = nowMs,
                            onPlay = onPlay,
                            emptyHint = "Select an addon with TV channels on the left",
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                    if (selected != null) {
                        Spacer(Modifier.width(16.dp))
                        LiveTvDetailPanel(
                            channel = selected,
                            state = state,
                            epg = remember(selected, epgState.revision, nowMs) { LiveTvEpgRepository.epgFor(selected, nowMs) },
                            nowMs = nowMs,
                            onPlay = onPlay,
                            modifier = Modifier.width(380.dp).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveTvSearchField(state: LiveTvUiState) {
    NuvioInputField(
        value = state.searchQuery,
        onValueChange = LiveTvRepository::setSearchQuery,
        placeholder = "Search ${state.filteredChannels.size} channels",
        trailingContent = {
            if (state.searchQuery.isNotEmpty()) {
                IconButton(onClick = { LiveTvRepository.setSearchQuery("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
            } else {
                Icon(Icons.Rounded.Search, contentDescription = null)
            }
        },
    )
}

/** Compact layout: categories as a horizontal chip row instead of the sidebar list. */
@Composable
private fun LiveTvCategoryChips(state: LiveTvUiState) {
    val tokens = MaterialTheme.nuvio
    val entries = buildList {
        add(LIVE_TV_CATEGORY_ALL to "All")
        if (state.favoriteKeys.isNotEmpty()) add(LIVE_TV_CATEGORY_FAVORITES to "★ Favorites")
        state.categories.forEach { add(it to it) }
    }
    if (entries.size <= 1) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(entries, key = { "chip:${it.first}" }) { (value, label) ->
            val chipSelected = state.selectedCategory.equals(value, ignoreCase = true)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (chipSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (chipSelected) tokens.colors.accent else tokens.colors.textPrimary,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (chipSelected) tokens.colors.accent.copy(alpha = 0.14f) else tokens.colors.surfaceCard)
                    .clickable { LiveTvRepository.setCategory(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun LiveTvSidebar(
    state: LiveTvUiState,
    epgState: LiveTvEpgUiState,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    LazyColumn(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(tokens.colors.surfaceCard)
            .padding(vertical = 8.dp),
    ) {
        item { SidebarLabel("Categories") }
        item {
            CategoryRow("All channels", state.selectedCategory == LIVE_TV_CATEGORY_ALL) {
                LiveTvRepository.setCategory(LIVE_TV_CATEGORY_ALL)
            }
        }
        if (state.favoriteKeys.isNotEmpty()) {
            item {
                CategoryRow("★ Favorites", state.selectedCategory == LIVE_TV_CATEGORY_FAVORITES) {
                    LiveTvRepository.setCategory(LIVE_TV_CATEGORY_FAVORITES)
                }
            }
        }
        items(state.categories, key = { "cat:$it" }) { category ->
            CategoryRow(category, state.selectedCategory.equals(category, ignoreCase = true)) {
                LiveTvRepository.setCategory(category)
            }
        }
        item { SidebarLabel("Addons") }
        if (state.availableAddons.isEmpty()) {
            item {
                Text(
                    text = "No addons installed",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        items(state.availableAddons, key = { "addon:${it.manifestUrl}" }) { addon ->
            CheckRow(
                title = addon.name,
                subtitle = if (addon.catalogCount > 0) "${addon.catalogCount} TV catalogs" else "No TV catalogs",
                checked = addon.isSelected,
                onToggle = { LiveTvRepository.toggleAddon(addon.manifestUrl) },
            )
        }
        item { SidebarLabel("Programme guide") }
        items(epgState.sources, key = { "epg:${it.id}" }) { source ->
            CheckRow(
                title = source.name,
                subtitle = null,
                checked = source.isEnabled,
                onToggle = { LiveTvEpgRepository.toggleSource(source.id) },
            )
        }
        item {
            val status = when {
                epgState.isLoading -> "Loading guide…"
                epgState.errorMessage != null -> epgState.errorMessage
                epgState.totalPrograms > 0 -> "${epgState.totalPrograms} programmes loaded"
                else -> null
            }
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        item { SidebarLabel("Content") }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { LiveTvRepository.setHideAdultChannels(!state.hideAdultChannels) }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Hide adult channels",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.hideAdultChannels, onCheckedChange = LiveTvRepository::setHideAdultChannels)
            }
        }
    }
}

@Composable
private fun SidebarLabel(text: String) {
    NuvioSectionLabel(
        text = text.uppercase(),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun CategoryRow(title: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) tokens.colors.accent else tokens.colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) tokens.colors.accent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun CheckRow(title: String, subtitle: String?, checked: Boolean, onToggle: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
            }
        }
    }
}

@Composable
private fun LiveTvChannelList(
    state: LiveTvUiState,
    epgRevision: Int,
    nowMs: Long,
    onPlay: (LiveTvChannel, LiveTvStreamOption) -> Unit,
    emptyHint: String,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Box(modifier = modifier) {
        when {
            state.isLoadingChannels && state.filteredChannels.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Loading channels…", color = tokens.colors.textMuted)
                }
            }
            state.filteredChannels.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = when {
                            state.selectedAddonUrls.isEmpty() -> emptyHint
                            state.searchQuery.isNotBlank() -> "No channel matches \"${state.searchQuery}\""
                            else -> state.channelsErrorMessage ?: "No channels"
                        },
                        color = tokens.colors.textMuted,
                    )
                }
            }
            else -> {
                val listState = rememberLazyListState()
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.filteredChannels, key = { it.stableKey() }) { channel ->
                        val epg = remember(channel, epgRevision, nowMs) { LiveTvEpgRepository.epgFor(channel, nowMs) }
                        LiveTvChannelRow(
                            channel = channel,
                            epg = epg,
                            nowMs = nowMs,
                            selected = state.selectedChannel?.stableKey() == channel.stableKey(),
                            favorite = state.isFavorite(channel),
                            onClick = {
                                if (state.selectedChannel?.stableKey() == channel.stableKey()) {
                                    state.streams.firstOrNull()?.let { onPlay(channel, it) }
                                } else {
                                    LiveTvRepository.selectChannel(channel)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveTvChannelRow(
    channel: LiveTvChannel,
    epg: ChannelEpgInfo?,
    nowMs: Long,
    selected: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) tokens.colors.accent.copy(alpha = 0.14f) else tokens.colors.surfaceCard)
            .then(if (selected) Modifier.border(1.dp, tokens.colors.accent, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(channel, Modifier.size(width = 72.dp, height = 44.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val now = epg?.nowProgram
            if (now != null) {
                Text(
                    text = "${now.title} · ${now.remainingMinutes(nowMs)} min left",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = now.progress(nowMs),
                    modifier = Modifier.fillMaxWidth(0.6f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                )
            } else {
                Text(
                    text = listOfNotNull(channel.primaryGenre ?: channel.catalogName, channel.addonName).distinct().joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = { LiveTvRepository.toggleFavorite(channel) }) {
            Icon(
                imageVector = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                tint = if (favorite) tokens.colors.warning else tokens.colors.textMuted,
            )
        }
    }
}

@Composable
private fun ChannelLogo(channel: LiveTvChannel, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tokens.colors.surfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        val logo = channel.displayLogo
        if (logo != null) {
            NuvioAsyncImage(
                model = logo,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        } else {
            Text(
                text = channel.name.take(2).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = tokens.colors.textMuted,
            )
        }
    }
}

@Composable
private fun LiveTvDetailPanel(
    channel: LiveTvChannel,
    state: LiveTvUiState,
    epg: ChannelEpgInfo?,
    nowMs: Long,
    onPlay: (LiveTvChannel, LiveTvStreamOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val upcoming = remember(epg, nowMs) {
        epg?.programs.orEmpty().filter { it.endEpochMs > nowMs }.take(24)
    }
    LazyColumn(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(tokens.colors.surfaceCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(channel, Modifier.size(width = 96.dp, height = 60.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.colors.textPrimary,
                    )
                    Text(
                        text = "${channel.catalogName} · ${channel.addonName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { LiveTvRepository.selectChannel(null) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = tokens.colors.textMuted)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { LiveTvRepository.adjacentChannel(-1)?.let(LiveTvRepository::selectChannel) }) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous channel", tint = tokens.colors.textPrimary)
                }
                IconButton(onClick = { LiveTvRepository.adjacentChannel(1)?.let(LiveTvRepository::selectChannel) }) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next channel", tint = tokens.colors.textPrimary)
                }
                val favorite = state.isFavorite(channel)
                IconButton(onClick = { LiveTvRepository.toggleFavorite(channel) }) {
                    Icon(
                        imageVector = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (favorite) tokens.colors.warning else tokens.colors.textPrimary,
                    )
                }
            }
        }
        item { NuvioSectionLabel("STREAMS") }
        when {
            state.isLoadingStreams -> item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Finding streams…", color = tokens.colors.textMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            state.streams.isEmpty() -> item {
                Text(
                    text = state.streamsErrorMessage ?: "No playable stream found for this channel",
                    color = tokens.colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            else -> items(state.streams, key = { "stream:${it.url}" }) { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(tokens.colors.surfaceElevated)
                        .clickable { onPlay(channel, option) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = tokens.colors.accent)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        option.subtitle?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.colors.textMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        item { NuvioSectionLabel("SCHEDULE", modifier = Modifier.padding(top = 8.dp)) }
        if (upcoming.isEmpty()) {
            item {
                Text(
                    text = if (epg == null) "No guide data for this channel" else "No upcoming programmes",
                    color = tokens.colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        items(upcoming) { program ->
            val live = program.isLive(nowMs)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (live) tokens.colors.accent.copy(alpha = 0.12f) else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = program.timeRange,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (live) tokens.colors.accent else tokens.colors.textMuted,
                    )
                    if (live) {
                        Spacer(Modifier.width(8.dp))
                        Text("LIVE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tokens.colors.danger)
                    }
                }
                Text(
                    text = program.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (live) FontWeight.SemiBold else FontWeight.Normal,
                    color = tokens.colors.textPrimary,
                )
                if (live && !program.description.isNullOrBlank()) {
                    Text(
                        text = program.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textSecondary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
