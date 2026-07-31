package com.aoscoremonitor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.jni.NativeSystemMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * The two groups of system-wide counters the kernel counters screen shows.
 *
 * There was a third holding this process's own /proc/self/status. It read the same file the memory
 * map screen reads, only unfiltered, so the two screens carried the same counters under different
 * headings. This screen is system-wide and that one is per-process; the split follows that line
 * now, which is also why the screen is no longer called "Native System Monitor" — the name said
 * how the reading is taken rather than what it is of.
 */
data class KernelCountersUiState(val cpu: Map<String, Long> = emptyMap(), val memory: Map<String, Long> = emptyMap())

/**
 * Polls [NativeSystemMonitor] once a second.
 *
 * The loop used to live in a `LaunchedEffect` on the screen, which tied it to the composition
 * rather than to whether anyone was looking: it kept reading /proc while the app sat in the
 * background. [WhileScreenVisible] ends the loop shortly after the screen stops.
 */
class KernelCountersViewModel : ViewModel() {

    private val monitor = NativeSystemMonitor()

    val uiState: StateFlow<KernelCountersUiState> = flow {
        while (true) {
            emit(
                KernelCountersUiState(
                    cpu = monitor.getCpuInfo(),
                    memory = monitor.getMemInfo()
                )
            )
            delay(REFRESH_INTERVAL_MS)
        }
    }.stateIn(viewModelScope, WhileScreenVisible, KernelCountersUiState())

    private companion object {
        const val REFRESH_INTERVAL_MS = 1_000L
    }
}
