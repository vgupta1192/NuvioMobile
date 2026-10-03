package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.jellyfin.JellyfinLauncher
import com.nuvio.app.features.jellyfin.JellyfinRepository
import com.nuvio.app.features.jellyfin.JellyfinUiState
import com.nuvio.app.features.livetv.LiveTvEpgRepository
import com.nuvio.app.features.livetv.LiveTvLauncher
import com.nuvio.app.features.livetv.LiveTvRepository

/**
 * Fork settings page that groups the two fork features (Jellyfin + Live TV) under one
 * "Extra" entry. Controls bind straight to the feature repositories — the same objects the
 * feature screens observe, so sign-in and toggles here are reflected there immediately.
 * Strings are hardcoded English on purpose (fork-only page, keeps the patch lean).
 */
internal fun LazyListScope.extraSettingsContent(isTablet: Boolean) {
    item { ExtraJellyfinSection(isTablet = isTablet) }
    item { ExtraLiveTvSection(isTablet = isTablet) }
}

@Composable
private fun ExtraJellyfinSection(isTablet: Boolean) {
    LaunchedEffect(Unit) { JellyfinRepository.initialize() }
    val state by JellyfinRepository.uiState.collectAsStateWithLifecycle()
    val session = state.session
    SettingsSection(title = "Jellyfin", isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            if (session == null) {
                ExtraJellyfinSignIn(state = state, isTablet = isTablet)
            } else {
                SettingsNavigationRow(
                    title = "Signed in as ${session.userName}",
                    description = session.serverUrl,
                    isTablet = isTablet,
                    onClick = { JellyfinLauncher.open() },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = "Open Jellyfin",
                    description = "Browse libraries, resume and play",
                    isTablet = isTablet,
                    onClick = { JellyfinLauncher.open() },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = "Sign out",
                    description = "Hides Jellyfin libraries, search results and streams",
                    isTablet = isTablet,
                    onClick = { JellyfinRepository.signOut() },
                )
            }
        }
        if (session != null && state.libraries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Libraries",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            SettingsGroup(isTablet = isTablet) {
                state.libraries.forEachIndexed { index, library ->
                    if (index > 0) SettingsGroupDivider(isTablet = isTablet)
                    SettingsSwitchRow(
                        title = library.name,
                        description = "Show this library in Jellyfin",
                        checked = library.id !in state.hiddenLibraryIds,
                        isTablet = isTablet,
                        onCheckedChange = { JellyfinRepository.toggleLibraryHidden(library.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtraJellyfinSignIn(state: JellyfinUiState, isTablet: Boolean) {
    val tokens = MaterialTheme.nuvio
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Connect your Jellyfin server",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = tokens.colors.textPrimary,
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

@Composable
private fun ExtraLiveTvSection(isTablet: Boolean) {
    LaunchedEffect(Unit) {
        LiveTvRepository.initialize()
        LiveTvEpgRepository.initialize()
    }
    val state by LiveTvRepository.uiState.collectAsStateWithLifecycle()
    val epgState by LiveTvEpgRepository.uiState.collectAsStateWithLifecycle()
    SettingsSection(title = "Live TV", isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = "Hide adult channels",
                description = "Filters adult channels out of the Live TV list",
                checked = state.hideAdultChannels,
                isTablet = isTablet,
                onCheckedChange = { LiveTvRepository.setHideAdultChannels(it) },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = "Open Live TV",
                description = "Channels, categories, addons and favorites",
                isTablet = isTablet,
                onClick = { LiveTvLauncher.open() },
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "TV guide sources (XMLTV)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        SettingsGroup(isTablet = isTablet) {
            epgState.sources.forEachIndexed { index, source ->
                if (index > 0) SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = source.name,
                    description = "Episode guide for matching channels",
                    checked = source.isEnabled,
                    isTablet = isTablet,
                    onCheckedChange = { LiveTvEpgRepository.toggleSource(source.id) },
                )
            }
        }
    }
}
