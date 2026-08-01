package com.aoscoremonitor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aoscoremonitor.R
import com.aoscoremonitor.diagnostics.DeviceIdentity
import com.aoscoremonitor.diagnostics.SystemInfoCollector
import com.aoscoremonitor.diagnostics.readDeviceIdentity
import com.aoscoremonitor.ui.components.InfoCard
import com.aoscoremonitor.ui.components.LabeledValue
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.SectionHeader
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.MonitorPreviews
import com.aoscoremonitor.ui.theme.MonitorTypography
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.SystemInfoViewModel
import com.aoscoremonitor.ui.viewmodel.monitorViewModel

@Composable
fun SystemInfoScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SystemInfoViewModel = monitorViewModel { SystemInfoViewModel(it) }
) {
    // collectAsStateWithLifecycle rather than collectAsState: the subscription ends when the
    // screen stops, which is what tells the view model to stop collecting.
    val systemInfo by viewModel.uiState.collectAsStateWithLifecycle()

    SystemInfoContent(
        systemInfo = systemInfo,
        // Read here rather than polled: these are compiled into the system image.
        device = remember { readDeviceIdentity() },
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
private fun SystemInfoContent(
    systemInfo: SystemInfoCollector.SystemInfo,
    device: DeviceIdentity,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    MonitorScaffold(
        title = stringResource(R.string.system_info_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            SectionHeader(
                title = stringResource(R.string.system_info_device_section),
                subtitle = stringResource(R.string.system_info_device_subtitle),
                icon = Icons.Default.PhoneAndroid
            )
            DeviceCard(device)

            SectionHeader(
                title = stringResource(R.string.system_info_live_section),
                subtitle = stringResource(R.string.system_info_live_subtitle),
                icon = Icons.Default.Speed
            )
            InfoCard(
                title = stringResource(R.string.system_info_cpu),
                value = systemInfo.cpuUsage,
                icon = Icons.Default.Speed
            )
            InfoCard(
                title = stringResource(R.string.system_info_memory),
                value = systemInfo.memoryUsage,
                icon = Icons.Default.Memory
            )
            InfoCard(
                title = stringResource(R.string.system_info_battery),
                value = systemInfo.batteryStatus,
                icon = Icons.Default.Battery6Bar
            )
            InfoCard(
                title = stringResource(R.string.system_info_network),
                value = systemInfo.networkStatus,
                icon = Icons.Default.NetworkCell
            )
        }
    }
}

/**
 * What the device says it is.
 *
 * The fingerprint last and in machine text: it is the one line here that is an identifier rather
 * than a fact to read, and it is long enough to wrap twice.
 */
@Composable
private fun DeviceCard(device: DeviceIdentity, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier, spacing = Spacing.Medium) {
        Text(text = device.name, style = MaterialTheme.typography.titleMedium)
        LabeledValue(
            label = stringResource(R.string.system_info_android),
            value = stringResource(R.string.system_info_android_value, device.androidRelease, device.sdkInt)
        )
        if (device.securityPatch.isNotEmpty()) {
            LabeledValue(
                label = stringResource(R.string.system_info_security_patch),
                value = device.securityPatch
            )
        }
        LabeledValue(label = stringResource(R.string.system_info_build), value = device.buildId)
        LabeledValue(
            label = stringResource(R.string.system_info_fingerprint),
            value = device.fingerprint,
            valueStyle = MonitorTypography.machineText
        )
    }
}

@MonitorPreviews
@Composable
private fun SystemInfoPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        SystemInfoContent(
            systemInfo = SystemInfoCollector.SystemInfo(
                cpuUsage = "2% (this app)",
                memoryUsage = "1686 MB available",
                batteryStatus = "100%",
                networkStatus = "Wi-Fi"
            ),
            device = DeviceIdentity(
                manufacturer = "Google",
                model = "Pixel 8",
                androidRelease = "16",
                sdkInt = 36,
                securityPatch = "2026-07-05",
                buildId = "BP41.250630.005",
                fingerprint = "google/shiba/shiba:16/BP41.250630.005/13456789:user/release-keys"
            ),
            onNavigateBack = {}
        )
    }
}
