package com.aoscoremonitor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.jni.NativeCredentialsInspector
import com.aoscoremonitor.diagnostics.jni.ProcessCredentials
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class ProcessCredentialsUiState(val credentials: ProcessCredentials? = null, val hasLoaded: Boolean = false)

/**
 * Reads the process's credentials once.
 *
 * Alone among the native screens this does not poll. Nothing here can change while the app runs: an
 * app process is forked from zygote with its uid, its groups, its seccomp filter and its SELinux
 * domain already set, and it holds no capability that would let it change any of them. A timer
 * would re-read the same answer every few seconds and say so by making the screen flicker.
 */
class ProcessCredentialsViewModel : ViewModel() {

    private val inspector = NativeCredentialsInspector()

    val uiState: StateFlow<ProcessCredentialsUiState> = flow {
        emit(ProcessCredentialsUiState(credentials = inspector.read(), hasLoaded = true))
    }.stateIn(viewModelScope, WhileScreenVisible, ProcessCredentialsUiState())
}
