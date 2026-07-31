package com.aoscoremonitor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.jni.CpuSnapshot
import com.aoscoremonitor.diagnostics.jni.NativeCpuInspector
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * The CPU as the screen shows it.
 *
 * [hasLoaded] separates "nothing read yet" from "read, and there was nothing" so the screen can
 * say which — the same distinction the network screen needed, for the same reason.
 */
data class CpuCoresUiState(val cpu: CpuSnapshot? = null, val hasLoaded: Boolean = false)

/**
 * Polls the per-core frequencies once a second, over topology read once.
 *
 * Core layout and instruction set cannot change while the process runs, so they are read on the
 * first collection and reused; only `scaling_cur_freq` and the governor are re-read. Reading the
 * whole of sysfs every second would be a dozen file opens per core for values known to be fixed.
 */
class CpuCoresViewModel : ViewModel() {

    private val inspector = NativeCpuInspector()

    val uiState: StateFlow<CpuCoresUiState> = flow {
        val topology = inspector.readStatic()
        if (topology == null) {
            emit(CpuCoresUiState(hasLoaded = true))
            return@flow
        }
        while (true) {
            emit(CpuCoresUiState(cpu = topology.withFrequencies(inspector.readFrequencies()), hasLoaded = true))
            delay(REFRESH_INTERVAL_MS)
        }
    }.stateIn(viewModelScope, WhileScreenVisible, CpuCoresUiState())

    private companion object {
        const val REFRESH_INTERVAL_MS = 1_000L
    }
}
