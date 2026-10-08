package com.nuvio.app.features.jellyfin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.navigation.JellyfinDetailRoute

/** Opens a Jellyfin item by id — the entry point for Jellyfin results in the global Search. */
@Composable
fun JellyfinDetailScreen(
    route: JellyfinDetailRoute,
    onBack: () -> Unit,
    onPlay: (JellyfinItem) -> Unit,
) {
    val state by JellyfinRepository.uiState.collectAsStateWithLifecycle()
    val tokens = MaterialTheme.nuvio

    LaunchedEffect(route.itemId) {
        JellyfinRepository.initialize()
        JellyfinRepository.selectItemById(route.itemId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.colors.background),
    ) {
        NuvioScreenHeader(
            title = state.selectedDetail?.name ?: route.title ?: "Jellyfin",
            onBack = onBack,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            when {
                state.selectedDetail == null && state.isLoadingDetail -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.selectedDetail == null && state.detailError != null -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Text(
                        text = state.detailError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                else -> JellyfinDetailPanel(
                    state = state,
                    onPlay = onPlay,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
