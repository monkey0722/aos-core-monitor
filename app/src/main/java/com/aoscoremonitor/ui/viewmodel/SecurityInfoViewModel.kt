package com.aoscoremonitor.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.SecurityInfoCollector
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Feeds the security screen from [SecurityInfoCollector].
 *
 * Enumerating installed packages and their permissions is the most expensive pass in the app, and
 * it was previously redone from scratch on every rotation.
 */
class SecurityInfoViewModel(context: Context) : ViewModel() {

    val uiState: StateFlow<SecurityInfoCollector.SecurityInfo> = callbackFlow {
        val collector = SecurityInfoCollector(context) { info -> trySend(info) }
        collector.startCollecting()
        awaitClose { collector.stopCollecting() }
    }.stateIn(viewModelScope, WhileScreenVisible, EMPTY)

    private companion object {
        val EMPTY = SecurityInfoCollector.SecurityInfo(
            selinuxStatus = "—",
            selinuxMode = "—",
            permissionMap = emptyMap(),
            hardwareSecurityInfo = SecurityInfoCollector.HardwareSecurityInfo(
                isHardwareBackedKeyStoreSupported = false,
                isStrongBoxBackedKeyStoreSupported = false,
                isFingerprintSupported = false,
                isBiometricSupported = false,
                isTeeSupported = false,
                keystoreVersion = "—"
            )
        )
    }
}
