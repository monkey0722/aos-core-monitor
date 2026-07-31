package com.aoscoremonitor.ui.viewmodel

import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.jni.NativeSystemMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** The three groups of counters the native monitor screen shows. */
data class NativeSystemUiState(
    val cpu: Map<String, Long> = emptyMap(),
    val memory: Map<String, Long> = emptyMap(),
    val process: Map<String, String> = emptyMap()
)

/**
 * Polls [NativeSystemMonitor] once a second.
 *
 * The loop used to live in a `LaunchedEffect` on the screen, which tied it to the composition
 * rather than to whether anyone was looking: it kept reading /proc while the app sat in the
 * background. [WhileScreenVisible] ends the loop shortly after the screen stops.
 */
class NativeSystemMonitorViewModel : ViewModel() {

    private val monitor = NativeSystemMonitor()

    val state: StateFlow<NativeSystemUiState> = flow {
        while (true) {
            emit(
                NativeSystemUiState(
                    cpu = monitor.getCpuInfo(),
                    memory = monitor.getMemInfo(),
                    process = monitor.getProcessInfo(Process.myPid())
                )
            )
            delay(REFRESH_INTERVAL_MS)
        }
    }.stateIn(viewModelScope, WhileScreenVisible, NativeSystemUiState())

    private companion object {
        const val REFRESH_INTERVAL_MS = 1_000L
    }
}
