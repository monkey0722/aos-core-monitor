package com.aoscoremonitor.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.Collected
import com.aoscoremonitor.diagnostics.HalInterfaceCollector
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/** Feeds the HAL screen from [HalInterfaceCollector]. */
class HalInfoViewModel(context: Context) : ViewModel() {

    val uiState: StateFlow<HalInterfaceCollector.HalData> = callbackFlow {
        val collector = HalInterfaceCollector(context) { data -> trySend(data) }
        collector.start()
        awaitClose { collector.stop() }
    }.stateIn(viewModelScope, WhileScreenVisible, EMPTY)

    private companion object {
        val EMPTY = HalInterfaceCollector.HalData(
            halInterfaces = Collected.real(emptyList()),
            hwServices = Collected.real(emptyList()),
            vndkInfo = HalInterfaceCollector.VndkInfo(version = null)
        )
    }
}
