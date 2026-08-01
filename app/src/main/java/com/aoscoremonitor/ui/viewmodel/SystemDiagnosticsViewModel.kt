package com.aoscoremonitor.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.SystemDiagnosticsCollector
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Feeds the diagnostics screen from [SystemDiagnosticsCollector].
 *
 * Each pass shells out to `dumpsys`, so [WhileScreenVisible] matters here: backgrounding the app
 * now stops the calls instead of leaving them running against a screen nobody is looking at.
 */
class SystemDiagnosticsViewModel(context: Context) : ViewModel() {

    val uiState: StateFlow<SystemDiagnosticsCollector.DiagnosticsInfo> = callbackFlow {
        val collector = SystemDiagnosticsCollector(context) { info -> trySend(info) }
        collector.start()
        awaitClose { collector.stop() }
    }.stateIn(viewModelScope, WhileScreenVisible, EMPTY)

    private companion object {
        val EMPTY = SystemDiagnosticsCollector.DiagnosticsInfo(
            runningProcesses = emptyList(),
            dumpsysResult = ""
        )
    }
}
