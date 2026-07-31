package com.aoscoremonitor.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.Collected
import com.aoscoremonitor.diagnostics.FrameworkAnalyzer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/** Feeds the framework screen from [FrameworkAnalyzer]. */
class FrameworkAnalysisViewModel(context: Context) : ViewModel() {

    val frameworkData: StateFlow<FrameworkAnalyzer.FrameworkData> = callbackFlow {
        val analyzer = FrameworkAnalyzer(context) { data -> trySend(data) }
        analyzer.startAnalyzing()
        awaitClose { analyzer.stopAnalyzing() }
    }.stateIn(viewModelScope, WhileScreenVisible, EMPTY)

    private companion object {
        val EMPTY = FrameworkAnalyzer.FrameworkData(
            binderTransactions = emptyList(),
            apiCalls = Collected.real(emptyList()),
            serviceData = Collected.real(
                FrameworkAnalyzer.ServiceManagerData(
                    runningServices = emptyMap(),
                    serviceConnections = emptyList()
                )
            )
        )
    }
}
