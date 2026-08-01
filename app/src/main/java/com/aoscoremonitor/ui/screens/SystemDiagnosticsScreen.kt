package com.aoscoremonitor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aoscoremonitor.R
import com.aoscoremonitor.diagnostics.SystemDiagnosticsCollector
import com.aoscoremonitor.ui.components.ExpandableTextCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.MonitorPreviews
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.SystemDiagnosticsViewModel
import com.aoscoremonitor.ui.viewmodel.monitorViewModel

@Composable
fun SystemDiagnosticsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SystemDiagnosticsViewModel = monitorViewModel { SystemDiagnosticsViewModel(it) }
) {
    val diagnostics by viewModel.uiState.collectAsStateWithLifecycle()

    SystemDiagnosticsContent(
        diagnostics = diagnostics,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
private fun SystemDiagnosticsContent(
    diagnostics: SystemDiagnosticsCollector.DiagnosticsInfo,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    MonitorScaffold(
        title = stringResource(R.string.diagnostics_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            item(key = "processes") {
                ExpandableTextCard(
                    title = stringResource(R.string.diagnostics_running_processes),
                    text = diagnostics.runningProcesses.joinToString("\n")
                        .ifEmpty { stringResource(R.string.diagnostics_no_processes) },
                    icon = Icons.AutoMirrored.Filled.List,
                    // The heading used to read "Running Processes" over a list that the platform
                    // has limited to one entry since Android 8 — this app's own process.
                    supportingText = stringResource(R.string.diagnostics_processes_notice)
                )
            }
            item(key = "dumpsys") {
                ExpandableTextCard(
                    title = stringResource(R.string.diagnostics_dumpsys),
                    text = diagnostics.dumpsysResult.ifEmpty { stringResource(R.string.diagnostics_loading) },
                    icon = Icons.Default.Terminal,
                    collapsedLines = 12
                )
            }
        }
    }
}

@MonitorPreviews
@Composable
private fun SystemDiagnosticsPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        SystemDiagnosticsContent(
            diagnostics = SystemDiagnosticsCollector.DiagnosticsInfo(
                runningProcesses = listOf("com.aoscoremonitor"),
                dumpsysResult = "Applications Memory Usage (in Kilobytes):\nUptime: 843211 Realtime: 843211"
            ),
            onNavigateBack = {}
        )
    }
}
