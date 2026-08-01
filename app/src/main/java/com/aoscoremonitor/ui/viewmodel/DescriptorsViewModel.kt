package com.aoscoremonitor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.jni.DescriptorSnapshot
import com.aoscoremonitor.diagnostics.jni.NativeDescriptorInspector
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * The descriptor table as it stands.
 *
 * [hasLoaded] separates "still reading" from "read, and there is nothing" — without it the screen
 * cannot word its empty state, because a process that reports no descriptors at all has either not
 * been asked yet or has answered something impossible.
 */
data class DescriptorsUiState(val snapshot: DescriptorSnapshot? = null, val hasLoaded: Boolean = false)

/** Polls the descriptor table every three seconds. */
class DescriptorsViewModel : ViewModel() {

    private val inspector = NativeDescriptorInspector()

    val uiState: StateFlow<DescriptorsUiState> = flow {
        while (true) {
            emit(DescriptorsUiState(snapshot = inspector.read(), hasLoaded = true))
            delay(REFRESH_INTERVAL_MS)
        }
    }.stateIn(viewModelScope, WhileScreenVisible, DescriptorsUiState())

    private companion object {
        // Descriptors open and close as the app loads assets and talks to binder, so this moves
        // faster than the mount table. Each pass is four syscalls per descriptor and there are
        // rarely more than a hundred, so three seconds is not work worth spreading out further.
        const val REFRESH_INTERVAL_MS = 3_000L
    }
}
