package com.aoscoremonitor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.jni.ModuleSnapshot
import com.aoscoremonitor.diagnostics.jni.NativeModuleInspector
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoadedLibrariesUiState(val modules: ModuleSnapshot? = null, val hasLoaded: Boolean = false, val isRefreshing: Boolean = false)

/**
 * Reads the loaded library list once, and again when asked.
 *
 * The other native screens poll, but the set of loaded objects is settled within a second of
 * startup and then changes only when something calls `dlopen` — polling it would serialise several
 * hundred entries a second to produce the same list. The screen offers a refresh instead, which is
 * also what makes a change visible: take a reading, do the thing that loads a library, refresh.
 */
class LoadedLibrariesViewModel : ViewModel() {

    private val inspector = NativeModuleInspector()
    private val _uiState = MutableStateFlow(LoadedLibrariesUiState())
    val uiState: StateFlow<LoadedLibrariesUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        // A second tap while a read is in flight would otherwise start a competing coroutine whose
        // result races the first one into the state.
        if (refreshJob?.isActive == true) return

        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            val snapshot = inspector.read()
            _uiState.value = LoadedLibrariesUiState(modules = snapshot, hasLoaded = true, isRefreshing = false)
        }
    }
}
