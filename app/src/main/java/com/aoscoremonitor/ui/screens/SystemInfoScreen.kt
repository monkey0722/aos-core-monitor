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
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aoscoremonitor.R
import com.aoscoremonitor.diagnostics.SystemInfoCollector
import com.aoscoremonitor.ui.components.InfoCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.MonitorPreviews
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
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
private fun SystemInfoContent(systemInfo: SystemInfoCollector.SystemInfo, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
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
            onNavigateBack = {}
        )
    }
}
