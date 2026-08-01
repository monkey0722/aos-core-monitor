package com.aoscoremonitor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aoscoremonitor.R
import com.aoscoremonitor.diagnostics.SecurityInfoCollector
import com.aoscoremonitor.ui.components.EmptyState
import com.aoscoremonitor.ui.components.LabeledValue
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.ReadingStatus
import com.aoscoremonitor.ui.components.SectionHeader
import com.aoscoremonitor.ui.components.StatusRow
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.MonitorPreviews
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.SecurityInfoViewModel
import com.aoscoremonitor.ui.viewmodel.monitorViewModel

@Composable
fun SecurityInfoScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SecurityInfoViewModel = monitorViewModel { SecurityInfoViewModel(it) }
) {
    val securityInfo by viewModel.uiState.collectAsStateWithLifecycle()

    SecurityInfoContent(
        securityInfo = securityInfo,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
private fun SecurityInfoContent(
    securityInfo: SecurityInfoCollector.SecurityInfo,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Materialising the map's entries is O(n); doing it inline in the `items` call redid it on
    // every recomposition, including the ones caused by scrolling.
    val apps = remember(securityInfo.permissionMap) { securityInfo.permissionMap.entries.toList() }

    MonitorScaffold(
        title = stringResource(R.string.security_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            item(key = "selinux-header") {
                SectionHeader(
                    title = stringResource(R.string.security_selinux_section),
                    subtitle = stringResource(R.string.security_selinux_subtitle),
                    icon = Icons.Default.Security
                )
            }
            item(key = "selinux") {
                SeLinuxCard(status = securityInfo.selinuxStatus, mode = securityInfo.selinuxMode)
            }

            item(key = "hardware-header") {
                SectionHeader(
                    title = stringResource(R.string.security_hardware_section),
                    subtitle = stringResource(R.string.security_hardware_subtitle),
                    icon = Icons.Default.Lock
                )
            }
            item(key = "hardware") {
                HardwareSecurityCard(hardwareInfo = securityInfo.hardwareSecurityInfo)
            }

            item(key = "permissions-header") {
                SectionHeader(
                    title = stringResource(R.string.security_permissions_section),
                    subtitle = stringResource(R.string.security_permissions_subtitle),
                    icon = Icons.Default.Info
                )
            }
            if (apps.isEmpty()) {
                item(key = "permissions-empty") {
                    EmptyState(message = stringResource(R.string.security_no_apps))
                }
            } else {
                items(apps, key = { it.key }) { (packageName, permissions) ->
                    AppPermissionCard(packageName = packageName, permissions = permissions)
                }
            }
        }
    }
}

/**
 * SELinux's mode, and whether that mode is the one you want.
 *
 * Enforcing is the healthy state, permissive is a working but weakened one, and anything else
 * means the mode could not be read at all. The card used to signal that through
 * primaryContainer / tertiaryContainer / errorContainer, which meant "good" was rendered in the
 * app's brand color and read as decoration.
 *
 * A mode that could not be read is [ReadingStatus.Neutral], not [ReadingStatus.Problem]. It used to
 * be the latter, which painted the card in the error color and said the device was in a bad state
 * when all that happened was that `getenforce` would not run from the app sandbox. Every other
 * screen in this app states why a reading is missing rather than colouring it as a fault, and the
 * note under the row is where that is said.
 */
@Composable
private fun SeLinuxCard(status: String, mode: String, modifier: Modifier = Modifier) {
    val enforcing = mode.contains("Enforcing", ignoreCase = true)
    val permissive = mode.contains("Permissive", ignoreCase = true)
    val readingStatus = when {
        enforcing -> ReadingStatus.Ok
        permissive -> ReadingStatus.Warning
        else -> ReadingStatus.Neutral
    }

    MonitorCard(modifier = modifier, status = readingStatus) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraLarge)
        ) {
            LabeledValue(
                label = stringResource(R.string.security_selinux_status),
                value = status,
                valueStyle = MaterialTheme.typography.titleMedium
            )
            LabeledValue(
                label = stringResource(R.string.security_selinux_mode),
                value = mode,
                valueStyle = MaterialTheme.typography.titleMedium
            )
        }
        if (!enforcing && !permissive) {
            Text(
                text = stringResource(R.string.security_selinux_unreadable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.Small)
            )
        }
    }
}

@Composable
private fun HardwareSecurityCard(hardwareInfo: SecurityInfoCollector.HardwareSecurityInfo, modifier: Modifier = Modifier) {
    val supported = stringResource(R.string.status_supported)
    val notSupported = stringResource(R.string.status_not_supported)

    @Composable
    fun feature(labelRes: Int, isSupported: Boolean) {
        StatusRow(
            label = stringResource(labelRes),
            status = if (isSupported) ReadingStatus.Ok else ReadingStatus.Neutral,
            statusDescription = if (isSupported) supported else notSupported
        )
    }

    MonitorCard(modifier = modifier) {
        feature(R.string.security_feature_hardware_keystore, hardwareInfo.isHardwareBackedKeyStoreSupported)
        feature(R.string.security_feature_strongbox, hardwareInfo.isStrongBoxBackedKeyStoreSupported)
        feature(R.string.security_feature_fingerprint, hardwareInfo.isFingerprintSupported)
        feature(R.string.security_feature_biometric, hardwareInfo.isBiometricSupported)
        feature(R.string.security_feature_tee, hardwareInfo.isTeeSupported)

        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.Small))

        LabeledValue(
            label = stringResource(R.string.security_keystore_implementation),
            value = hardwareInfo.keystoreVersion
        )
    }
}

@Composable
private fun AppPermissionCard(
    packageName: String,
    permissions: List<SecurityInfoCollector.AppPermissionInfo>,
    modifier: Modifier = Modifier
) {
    val dangerousCount = remember(permissions) {
        permissions.count { it.isProtectionDangerous && it.isGranted }
    }
    val shown = remember(permissions) { permissions.take(PERMISSIONS_SHOWN) }

    MonitorCard(
        modifier = modifier,
        status = if (dangerousCount > 0) ReadingStatus.Problem else ReadingStatus.Neutral
    ) {
        Text(
            text = packageName,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = pluralStringResource(R.plurals.security_dangerous_permissions, dangerousCount, dangerousCount),
            style = MaterialTheme.typography.bodyMedium
        )

        shown.forEach { permission ->
            val status = when {
                permission.isGranted && permission.isProtectionDangerous -> ReadingStatus.Problem
                permission.isGranted -> ReadingStatus.Ok
                else -> ReadingStatus.Neutral
            }
            StatusRow(
                // The android.permission prefix is the same on every row and only makes the
                // distinguishing part harder to find.
                label = permission.permissionName.substringAfterLast('.'),
                status = status,
                statusDescription = stringResource(
                    when (status) {
                        ReadingStatus.Problem -> R.string.security_permission_granted_dangerous
                        ReadingStatus.Ok -> R.string.security_permission_granted
                        else -> R.string.security_permission_not_granted
                    }
                )
            )
        }

        if (permissions.size > PERMISSIONS_SHOWN) {
            val remaining = permissions.size - PERMISSIONS_SHOWN
            Text(
                text = pluralStringResource(R.plurals.security_more_permissions, remaining, remaining),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Enough to characterise an app without turning the list into a permission dump. */
private const val PERMISSIONS_SHOWN = 3

@MonitorPreviews
@Composable
private fun SecurityInfoPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        SecurityInfoContent(
            securityInfo = SecurityInfoCollector.SecurityInfo(
                selinuxStatus = "Enabled",
                selinuxMode = "Enforcing",
                permissionMap = mapOf(
                    "com.example.camera" to listOf(
                        SecurityInfoCollector.AppPermissionInfo("android.permission.CAMERA", true, true),
                        SecurityInfoCollector.AppPermissionInfo("android.permission.INTERNET", true, false)
                    )
                ),
                hardwareSecurityInfo = SecurityInfoCollector.HardwareSecurityInfo(
                    isHardwareBackedKeyStoreSupported = true,
                    isStrongBoxBackedKeyStoreSupported = false,
                    isFingerprintSupported = true,
                    isBiometricSupported = true,
                    isTeeSupported = true,
                    keystoreVersion = "Keymaster 4.1"
                )
            ),
            onNavigateBack = {}
        )
    }
}
