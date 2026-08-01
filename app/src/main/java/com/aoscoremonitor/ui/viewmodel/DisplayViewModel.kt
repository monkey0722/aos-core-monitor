package com.aoscoremonitor.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.DisplayInfo
import com.aoscoremonitor.diagnostics.displayChanges
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DisplayUiState(val display: DisplayInfo? = null, val hasLoaded: Boolean = false)

/**
 * The panel, and every change to it.
 *
 * No timer: rotating the device, switching mode and letting the screen doze are the only things
 * that change any of this, and the platform reports all three. What a poll would add is a wake-up
 * per second to find that a panel is still the same panel.
 */
class DisplayViewModel(context: Context) : ViewModel() {

    val uiState: StateFlow<DisplayUiState> = displayChanges(context)
        .map { display -> DisplayUiState(display = display, hasLoaded = true) }
        .stateIn(viewModelScope, WhileScreenVisible, DisplayUiState())
}
