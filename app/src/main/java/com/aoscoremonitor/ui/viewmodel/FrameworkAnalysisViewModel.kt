package com.aoscoremonitor.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.Collected
import com.aoscoremonitor.diagnostics.FrameworkCollector
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/** Feeds the framework screen from [FrameworkCollector]. */
class FrameworkAnalysisViewModel(context: Context) : ViewModel() {

    val uiState: StateFlow<FrameworkCollector.FrameworkData> = callbackFlow {
        val collector = FrameworkCollector(context) { data -> trySend(data) }
        collector.start()
        awaitClose { collector.stop() }
    }.stateIn(viewModelScope, WhileScreenVisible, EMPTY)

    private companion object {
        val EMPTY = FrameworkCollector.FrameworkData(
            binderTransactions = emptyList(),
            apiCalls = Collected.real(emptyList()),
            serviceData = Collected.real(
                FrameworkCollector.ServiceManagerData(
                    runningServices = emptyMap(),
                    serviceConnections = emptyList()
                )
            )
        )
    }
}
