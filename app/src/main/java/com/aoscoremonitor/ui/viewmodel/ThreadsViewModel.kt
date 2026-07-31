package com.aoscoremonitor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.jni.NativeThreadInspector
import com.aoscoremonitor.diagnostics.jni.ThreadSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * The thread list, and whether a reading has been taken yet.
 *
 * [hasLoaded] separates "still reading" from "the native library did not load", which look the same
 * to a screen holding an empty snapshot and mean opposite things to whoever is looking at it.
 */
data class ThreadsUiState(val snapshot: ThreadSnapshot? = null, val hasLoaded: Boolean = false)

/** Polls the thread list once a second. */
class ThreadsViewModel : ViewModel() {

    private val inspector = NativeThreadInspector()

    val uiState: StateFlow<ThreadsUiState> = flow {
        while (true) {
            emit(ThreadsUiState(snapshot = inspector.read(), hasLoaded = true))
            delay(REFRESH_INTERVAL_MS)
        }
    }.stateIn(viewModelScope, WhileScreenVisible, ThreadsUiState())

    private companion object {
        // Threads come and go in well under a second — a poll slower than this shows a list that
        // was true a moment ago, which for a screen about what is running now is the wrong trade.
        const val REFRESH_INTERVAL_MS = 1_000L
    }
}
