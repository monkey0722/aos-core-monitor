package com.aoscoremonitor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.jni.NativeVulkanInspector
import com.aoscoremonitor.diagnostics.jni.VulkanSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VulkanUiState(val vulkan: VulkanSnapshot? = null, val hasLoaded: Boolean = false, val isRefreshing: Boolean = false)

/**
 * Reads the Vulkan device properties once, and again when asked.
 *
 * Nothing here changes while the app runs — a physical device's limits and driver are fixed until
 * the driver itself is replaced — so polling would create and destroy an instance every second to
 * produce the same answer. The refresh exists for the one case that does change it: a driver
 * updated underneath a running app through the GPU driver update path.
 */
class VulkanViewModel : ViewModel() {

    private val inspector = NativeVulkanInspector()
    private val _uiState = MutableStateFlow(VulkanUiState())
    val uiState: StateFlow<VulkanUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        // Creating a second instance while the first read is still in flight would have two
        // coroutines racing their results into the state.
        if (refreshJob?.isActive == true) return

        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            val snapshot = inspector.read()
            _uiState.value = VulkanUiState(vulkan = snapshot, hasLoaded = true, isRefreshing = false)
        }
    }
}
