package com.aoscoremonitor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.jni.AudioPathSnapshot
import com.aoscoremonitor.diagnostics.jni.NativeAudioInspector
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AudioPathUiState(val audio: AudioPathSnapshot? = null, val hasLoaded: Boolean = false, val isRefreshing: Boolean = false)

/**
 * Opens the four probe streams once, and again when asked.
 *
 * This one has a reason to be re-read that the other native screens do not: what the system grants
 * depends on what else is playing. An exclusive request that comes back shared while music is
 * running may be granted once the other app lets go, and the refresh is how that becomes visible.
 *
 * It does not poll. Opening four streams a second to watch for that would be a stream of open and
 * close calls against the audio HAL for a reading that changes when the user changes it.
 */
class AudioPathViewModel : ViewModel() {

    private val inspector = NativeAudioInspector()
    private val _uiState = MutableStateFlow(AudioPathUiState())
    val uiState: StateFlow<AudioPathUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        // Two sets of probe streams open at once would contend for the very exclusive path they
        // are measuring, so the second tap would read the first one's answer.
        if (refreshJob?.isActive == true) return

        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            val snapshot = inspector.read()
            _uiState.value = AudioPathUiState(audio = snapshot, hasLoaded = true, isRefreshing = false)
        }
    }
}
