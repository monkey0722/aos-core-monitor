package com.aoscoremonitor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.Collected
import com.aoscoremonitor.diagnostics.jni.NativeSystemMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Per-interface counters, and whether the first read has come back yet.
 *
 * The screen previously tracked a `refreshing` flag that was set and cleared around each poll, so
 * it flickered true for a moment every two seconds. What the empty state actually needs to know
 * is whether anything has been read at all — hence [hasLoaded].
 */
data class NetworkStatsUiState(
    val stats: Collected<Map<String, NativeSystemMonitor.InterfaceStats>> = Collected.real(emptyMap()),
    val hasLoaded: Boolean = false
)

/** Polls [NativeSystemMonitor.getNetworkStats] every two seconds while the screen is visible. */
class NetworkStatsViewModel : ViewModel() {

    private val monitor = NativeSystemMonitor()

    val state: StateFlow<NetworkStatsUiState> = flow {
        while (true) {
            emit(NetworkStatsUiState(stats = monitor.getNetworkStats(), hasLoaded = true))
            delay(REFRESH_INTERVAL_MS)
        }
    }.stateIn(viewModelScope, WhileScreenVisible, NetworkStatsUiState())

    private companion object {
        const val REFRESH_INTERVAL_MS = 2_000L
    }
}
