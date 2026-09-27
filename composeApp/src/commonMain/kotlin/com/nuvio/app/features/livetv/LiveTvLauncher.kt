package com.nuvio.app.features.livetv

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Lets the sidebar entry open the Live TV route without threading a callback through the shell. */
object LiveTvLauncher {
    private val _openRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openRequests: SharedFlow<Unit> = _openRequests.asSharedFlow()

    fun open() {
        _openRequests.tryEmit(Unit)
    }
}
