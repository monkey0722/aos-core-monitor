package com.aoscoremonitor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.Collected
import com.aoscoremonitor.diagnostics.jni.NativeSystemMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** The connection table, and whether the first read has come back yet. */
data class TcpConnectionsUiState(
    val connections: Collected<List<NativeSystemMonitor.TcpConnection>> = Collected.real(emptyList()),
    val hasLoaded: Boolean = false
) {
    val established: Int get() = connections.value.count { it.status == "ESTABLISHED" }
    val listening: Int get() = connections.value.count { it.status == "LISTEN" }
    val waiting: Int get() = connections.value.count { it.status == "TIME_WAIT" || it.status == "CLOSE_WAIT" }
    val total: Int get() = connections.value.size
}

/** Polls [NativeSystemMonitor.getTcpConnections] every three seconds while the screen is visible. */
class TcpConnectionsViewModel : ViewModel() {

    private val monitor = NativeSystemMonitor()

    val state: StateFlow<TcpConnectionsUiState> = flow {
        while (true) {
            emit(TcpConnectionsUiState(connections = monitor.getTcpConnections(), hasLoaded = true))
            delay(REFRESH_INTERVAL_MS)
        }
    }.stateIn(viewModelScope, WhileScreenVisible, TcpConnectionsUiState())

    private companion object {
        const val REFRESH_INTERVAL_MS = 3_000L
    }
}
