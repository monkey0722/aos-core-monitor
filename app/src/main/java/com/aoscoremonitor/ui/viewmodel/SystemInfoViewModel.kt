package com.aoscoremonitor.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.SystemInfoCollector
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Feeds the system info screen from [SystemInfoCollector].
 *
 * The collector was previously built inside the composable with `remember`, so a rotation threw
 * away every reading gathered so far and started over. Holding it here keeps it across the
 * configuration change, while [WhileScreenVisible] stops it once the screen goes away.
 */
class SystemInfoViewModel(context: Context) : ViewModel() {

    val uiState: StateFlow<SystemInfoCollector.SystemInfo> = callbackFlow {
        val collector = SystemInfoCollector(context) { info -> trySend(info) }
        collector.start()
        awaitClose { collector.stop() }
    }.stateIn(viewModelScope, WhileScreenVisible, EMPTY)

    private companion object {
        /** Shown until the first reading lands, one collection interval in. */
        val EMPTY = SystemInfoCollector.SystemInfo(
            cpuUsage = "—",
            memoryUsage = "—",
            batteryStatus = "—",
            networkStatus = "—"
        )
    }
}
