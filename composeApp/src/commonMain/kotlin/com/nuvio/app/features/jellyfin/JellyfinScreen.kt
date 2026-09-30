package com.nuvio.app.features.jellyfin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioSectionLabel
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.nuvio
import kotlin.math.roundToInt

@Composable
fun JellyfinScreen(
    onBack: () -> Unit,
    onPlay: (JellyfinItem) -> Unit,
) {
    LaunchedEffect(Unit) { JellyfinRepository.initialize() }
    val state by JellyfinRepository.uiState.collectAsStateWithLifecycle()
    val tokens = MaterialTheme.nuvio
    val session = state.session

    if (session == null) {
        JellyfinSignInContent(state = state, onBack = onBack)
        return
    }

    // Phones get one pane at a time (browse / libraries / detail); tablets keep the 3-column layout.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(tokens.colors.background)) {
        val compact = maxWidth < 840.dp
        var showLibraries by rememberSaveable { mutableStateOf(false) }
        var showHiddenLibraries by rememberSaveable { mutableStateOf(false) }
        val selected = state.selectedDetail
        val compactDetailOpen = compact && selected != null
        PlatformBackHandler(enabled = compact && (showLibraries || compactDetailOpen)) {
            if (showLibraries) showLibraries = false else JellyfinRepository.clearSelection()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            NuvioScreenHeader(
                title = when {
                    compactDetailOpen -> selected?.name ?: "Jellyfin"
                    compact && showLibraries -> "Libraries"
                    else -> "Jellyfin · ${session.serverName}"
                },
                onBack = {
                    when {
                        compact && showLibraries -> showLibraries = false
                        compactDetailOpen -> JellyfinRepository.clearSelection()
                        else -> onBack()
                    }
                },
                actions = {
                    if (compact && !compactDetailOpen && !showLibraries) {
                        IconButton(onClick = { showLibraries = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Libraries",
                                tint = tokens.colors.textPrimary,
                            )
                        }
                    }
                    IconButton(onClick = { JellyfinRepository.refresh() }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            tint = tokens.colors.textPrimary,
                        )
                    }
                    IconButton(onClick = { JellyfinRepository.signOut() }) {
                        Icon(
                            imageVector = Icons.Rounded.Logout,
                            contentDescription = "Sign out",
                            tint = tokens.colors.textPrimary,
                        )
                    }
                },
            )
            if (compact) {
                when {
                    showLibraries -> JellyfinSidebarContent(
                        state = state,
                        showHiddenLibraries = showHiddenLibraries,
                        onToggleHidden = { showHiddenLibraries = !showHiddenLibraries },
                        onLibrarySelected = { showLibraries = false },
                        modifier = Modifier.fillMaxSize().padding(bottom = 16.dp),
                    )

                    compactDetailOpen -> JellyfinDetailPanel(
                        state = state,
                        onPlay = onPlay,
                        modifier = Modifier.fillMaxSize().padding(bottom = 16.dp),
                    )

                    else -> Column(modifier = Modifier.fillMaxSize().padding(bottom = 8.dp)) {
                        NuvioInputField(
                            value = state.searchQuery,
                            onValueChange = JellyfinRepository::setSearchQuery,
                            placeholder = "Search your Jellyfin server…",
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        JellyfinLibraryChips(state = state)
                        Spacer(modifier = Modifier.height(10.dp))
                        JellyfinItemGrid(
                            state = state,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    JellyfinSidebarContent(
                        state = state,
                        showHiddenLibraries = showHiddenLibraries,
                        onToggleHidden = { showHiddenLibraries = !showHiddenLibraries },
                        modifier = Modifier.width(232.dp).fillMaxHeight(),
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        NuvioInputField(
                            value = state.searchQuery,
                            onValueChange = JellyfinRepository::setSearchQuery,
                            placeholder = "Search your Jellyfin server…",
                        )
                        JellyfinLibraryChips(state = state)
                        JellyfinItemGrid(
                            state = state,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                    JellyfinDetailPanel(
                        state = state,
                        onPlay = onPlay,
                        modifier = Modifier.width(336.dp).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/** Horizontal library switcher — always visible in the browse view. */
@Composable
private fun JellyfinLibraryChips(state: JellyfinUiState) {
    val visible = state.libraries.filter { it.id !in state.hiddenLibraryIds }
    if (visible.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visible.forEach { library ->
            JellyfinSortChip(
                label = library.name,
                isSelected = state.selectedLibraryId == library.id,
                onClick = { JellyfinRepository.selectLibrary(library.id) },
            )
        }
    }
}

@Composable
private fun JellyfinItemGrid(
    state: JellyfinUiState,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Box(modifier = modifier) {
        when {
            state.isLoadingItems && state.items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.itemsError != null && state.items.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.itemsError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { JellyfinRepository.retryItems() }) {
                    Text(text = "Retry")
                }
            }

            state.items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nothing here yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                )
            }

            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    JellyfinSortChip(
                        label = "A–Z",
                        isSelected = !state.sortLatestFirst,
                        onClick = { JellyfinRepository.setSortLatestFirst(false) },
                    )
                    JellyfinSortChip(
                        label = "Latest",
                        isSelected = state.sortLatestFirst,
                        onClick = { JellyfinRepository.setSortLatestFirst(true) },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (state.totalItemCount > 0) {
                            "${state.items.size} of ${state.totalItemCount}"
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.colors.textMuted,
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = state.items, key = { it.id }) { item ->
                        JellyfinPosterCell(
                            item = item,
                            posterUrl = JellyfinRepository.posterUrlFor(item),
                            isSelected = state.selectedItemId == item.id,
                            onClick = { JellyfinRepository.selectItem(item) },
                        )
                    }
                    if (state.canLoadMore) {
                        item(key = "jellyfin_load_more", span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Button(
                                    enabled = !state.isLoadingItems,
                                    onClick = { JellyfinRepository.loadMore() },
                                ) {
                                    if (state.isLoadingItems) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White,
                                        )
                                    } else {
                                        Text(text = "Load more")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JellyfinSidebarContent(
    state: JellyfinUiState,
    showHiddenLibraries: Boolean,
    onToggleHidden: () -> Unit,
    modifier: Modifier = Modifier,
    onLibrarySelected: (() -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Signed in as ${state.session?.userName ?: ""}",
            style = MaterialTheme.typography.labelMedium,
            color = tokens.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(8.dp))
        NuvioSectionLabel(text = "Libraries")
        Spacer(modifier = Modifier.height(4.dp))
        val visibleLibraries = state.libraries.filter { it.id !in state.hiddenLibraryIds }
        val hiddenLibraries = state.libraries.filter { it.id in state.hiddenLibraryIds }
        visibleLibraries.forEach { library ->
            JellyfinLibraryRow(
                name = library.name,
                isSelected = state.selectedLibraryId == library.id,
                onToggleVisibility = { JellyfinRepository.toggleLibraryHidden(library.id) },
                onClick = {
                    JellyfinRepository.selectLibrary(library.id)
                    onLibrarySelected?.invoke()
                },
            )
        }
        if (state.libraries.isEmpty() && state.isLoadingItems) {
            Text(
                text = "Loading libraries…",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
            )
        }
        if (hiddenLibraries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleHidden)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = (if (showHiddenLibraries) "▾ " else "▸ ") + "Hidden (${hiddenLibraries.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.colors.textMuted,
                )
            }
            if (showHiddenLibraries) {
                hiddenLibraries.forEach { library ->
                    JellyfinLibraryRow(
                        name = library.name,
                        isSelected = false,
                        isDimmed = true,
                        onToggleVisibility = { JellyfinRepository.toggleLibraryHidden(library.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun JellyfinSignInContent(
    state: JellyfinUiState,
    onBack: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.colors.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(tokens.colors.surfaceCard)
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Connect your Jellyfin server",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = tokens.colors.textPrimary,
                )
                Text(
                    text = "Sign in with your Jellyfin username and password. Your libraries show up here and Jellyfin results are included in Search.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                )
                NuvioInputField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    placeholder = "Server address (https://…)",
                )
                NuvioInputField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = "Username",
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = {
                        Text(
                            text = "Password",
                            color = tokens.colors.textMuted,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = tokens.colors.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tokens.colors.borderFocus,
                        unfocusedBorderColor = tokens.colors.borderDefault,
                        focusedContainerColor = tokens.colors.surfaceElevated,
                        unfocusedContainerColor = tokens.colors.surfaceElevated,
                        cursorColor = tokens.colors.accent,
                    ),
                )
                Button(
                    enabled = !state.isLoadingSession && serverUrl.isNotBlank() && username.isNotBlank(),
                    onClick = { JellyfinRepository.signIn(serverUrl, username, password) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.colors.accent),
                ) {
                    if (state.isLoadingSession) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text(text = "Sign in")
                    }
                }
                if (state.sessionError != null) {
                    Text(
                        text = state.sessionError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun JellyfinLibraryRow(
    name: String,
    isSelected: Boolean,
    isDimmed: Boolean = false,
    onToggleVisibility: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) tokens.colors.surfaceElevated else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 10.dp, top = 2.dp, end = 2.dp, bottom = 2.dp)
            .alpha(if (isDimmed) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (isSelected) tokens.colors.textPrimary else tokens.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onToggleVisibility != null) {
            IconButton(
                onClick = onToggleVisibility,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    imageVector = if (isDimmed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (isDimmed) "Show library" else "Hide library",
                    tint = tokens.colors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun JellyfinSortChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (isSelected) tokens.colors.accent else tokens.colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) Color.White else tokens.colors.textMuted,
        )
    }
}

@Composable
private fun JellyfinPosterCell(
    item: JellyfinItem,
    posterUrl: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(tokens.colors.surfaceElevated),
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = item.name.take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.colors.textMuted,
                    )
                }
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(tokens.colors.accent.copy(alpha = 0.25f)),
                )
            }
            val progress = item.playedPercentage
            if (progress != null && progress > 1.0 && progress < 95.0) {
                LinearProgressIndicator(
                    progress = { (progress / 100.0).toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.productionYear != null) {
            Text(
                text = item.productionYear.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
            )
        }
    }
}

/** Shared detail panel: used by the Jellyfin screen's detail pane and the standalone detail route. */
@Composable
internal fun JellyfinDetailPanel(
    state: JellyfinUiState,
    onPlay: (JellyfinItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val item = state.selectedDetail
    if (item == null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(tokens.colors.surfaceCard)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Select an item to see its details",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(tokens.colors.surfaceCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "jellyfin_detail_header") {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .width(128.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tokens.colors.surfaceElevated),
                ) {
                    val posterUrl = JellyfinRepository.posterUrlFor(item, maxWidth = 400)
                    if (posterUrl != null) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = tokens.colors.textPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.isEpisode && !item.seriesName.isNullOrBlank()) {
                        Text(
                            text = item.seriesName.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val metaLine = buildList {
                        item.productionYear?.let { add(it.toString()) }
                        item.communityRating?.let { rating ->
                            add("★ ${(rating * 10).roundToInt() / 10.0}")
                        }
                        item.runTimeMinutes?.let { minutes -> add("${minutes}m") }
                        item.officialRating?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }.joinToString("  ·  ")
                    if (metaLine.isNotBlank()) {
                        Text(
                            text = metaLine,
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.colors.textMuted,
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    JellyfinPlayButton(item = item, state = state, onPlay = onPlay)
                    if (item.resumePositionMs > 0) {
                        Text(
                            text = "Resumes at ${formatPosition(item.resumePositionMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.colors.textMuted,
                        )
                    }
                }
            }
        }
        if (!item.overview.isNullOrBlank()) {
            item(key = "jellyfin_detail_overview") {
                Text(
                    text = item.overview.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textPrimary,
                )
            }
        }
        if (item.isSeries) {
            item(key = "jellyfin_detail_seasons") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NuvioSectionLabel(text = "Seasons")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        state.seasons.forEach { season ->
                            JellyfinSortChip(
                                label = season.name ?: "Season ${season.indexNumber ?: ""}",
                                isSelected = state.selectedSeasonId == season.id,
                                onClick = { JellyfinRepository.selectSeason(season.id) },
                            )
                        }
                    }
                }
            }
        }
        if (item.isSeries || item.isFolder) {
            item(key = "jellyfin_detail_episodes_label") {
                NuvioSectionLabel(text = if (item.isSeries) "Episodes" else "Files in this folder")
            }
            if (state.isLoadingDetail && state.episodes.isEmpty()) {
                item(key = "jellyfin_detail_episodes_loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
            }
            listItems(items = state.episodes, key = { it.id }) { episode ->
                JellyfinEpisodeRow(episode = episode, onPlay = onPlay)
            }
        }
    }
}

@Composable
private fun JellyfinPlayButton(
    item: JellyfinItem,
    state: JellyfinUiState,
    onPlay: (JellyfinItem) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val playable: JellyfinItem? = when {
        item.isSeries || item.isFolder ->
            state.episodes.firstOrNull { it.resumePositionMs > 0 } ?: state.episodes.firstOrNull()
        item.isPlayable -> item
        else -> null // box sets etc. have no stream of their own
    }
    Button(
        enabled = playable != null,
        onClick = { playable?.let(onPlay) },
        colors = ButtonDefaults.buttonColors(containerColor = tokens.colors.accent),
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = when {
                playable == null && item.isSeries -> "No episodes"
                playable == null && item.isFolder -> "No files"
                playable == null -> "Not playable"
                playable.resumePositionMs > 0 -> {
                    if (playable.isEpisode) "Resume ${episodeLabel(playable)}" else "Resume"
                }
                playable.isEpisode -> "Play ${episodeLabel(playable)}"
                else -> "Play"
            },
        )
    }
}

private fun episodeLabel(episode: JellyfinItem): String {
    val season = episode.parentIndexNumber
    val number = episode.indexNumber
    return when {
        season != null && number != null -> "S$season·E$number"
        number != null -> "E$number"
        else -> ""
    }.takeIf { it.isNotBlank() } ?: ""
}

@Composable
private fun JellyfinEpisodeRow(
    episode: JellyfinItem,
    onPlay: (JellyfinItem) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.colors.surfaceElevated)
            .clickable { onPlay(episode) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                episode.isEpisode -> episodeLabel(episode).ifBlank { "E${episode.indexNumber ?: ""}" }
                episode.productionYear != null -> episode.productionYear.toString()
                else -> "•"
            },
            style = MaterialTheme.typography.labelMedium,
            color = tokens.colors.textMuted,
            modifier = Modifier.width(52.dp),
            maxLines = 1,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = episode.name,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                episode.runTimeMinutes?.let { minutes ->
                    Text(
                        text = "${minutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.colors.textMuted,
                    )
                }
                if (episode.resumePositionMs > 0) {
                    Text(
                        text = "resume at ${formatPosition(episode.resumePositionMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.colors.accent,
                    )
                }
            }
            val progress = episode.playedPercentage
            if (progress != null && progress > 1.0 && progress < 95.0) {
                LinearProgressIndicator(
                    progress = { (progress / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }
        }
    }
}

internal fun formatPosition(positionMs: Long): String {
    val totalSeconds = positionMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        "${hours}h ${minutes.toString().padStart(2, '0')}m"
    } else {
        "${minutes}m"
    }
}
