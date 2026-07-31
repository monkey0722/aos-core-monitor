package com.aoscoremonitor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.jni.MemorySnapshot
import com.aoscoremonitor.diagnostics.jni.NativeMemoryInspector
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class MemoryMapUiState(val memory: MemorySnapshot? = null, val hasLoaded: Boolean = false)

/**
 * Polls this process's memory map every two seconds.
 *
 * Slower than the CPU screen because the read is heavier — `/proc/self/maps` is hundreds of lines
 * and is walked in full — and because a process's own footprint moves in steps rather than
 * continuously. [WhileScreenVisible] stops it when the screen goes away.
 */
class MemoryMapViewModel : ViewModel() {

    private val inspector = NativeMemoryInspector()

    val uiState: StateFlow<MemoryMapUiState> = flow {
        while (true) {
            emit(MemoryMapUiState(memory = inspector.read(), hasLoaded = true))
            delay(REFRESH_INTERVAL_MS)
        }
    }.stateIn(viewModelScope, WhileScreenVisible, MemoryMapUiState())

    private companion object {
        const val REFRESH_INTERVAL_MS = 2_000L
    }
}
