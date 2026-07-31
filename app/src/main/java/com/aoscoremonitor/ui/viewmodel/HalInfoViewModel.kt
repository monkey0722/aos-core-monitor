package com.aoscoremonitor.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.Collected
import com.aoscoremonitor.diagnostics.HalInterfaceAnalyzer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/** Feeds the HAL screen from [HalInterfaceAnalyzer]. */
class HalInfoViewModel(context: Context) : ViewModel() {

    val halData: StateFlow<HalInterfaceAnalyzer.HalData> = callbackFlow {
        val analyzer = HalInterfaceAnalyzer(context) { data -> trySend(data) }
        analyzer.startAnalyzing()
        awaitClose { analyzer.stopAnalyzing() }
    }.stateIn(viewModelScope, WhileScreenVisible, EMPTY)

    private companion object {
        val EMPTY = HalInterfaceAnalyzer.HalData(
            halInterfaces = Collected.real(emptyList()),
            hwservices = Collected.real(emptyList()),
            vndkInfo = HalInterfaceAnalyzer.VndkInfo(version = "—", libraries = emptyList())
        )
    }
}
