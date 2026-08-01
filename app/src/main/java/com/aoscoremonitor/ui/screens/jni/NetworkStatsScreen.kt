package com.aoscoremonitor.ui.screens.jni

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aoscoremonitor.R
import com.aoscoremonitor.diagnostics.Collected
import com.aoscoremonitor.diagnostics.jni.NativeSystemMonitor
import com.aoscoremonitor.ui.components.FullScreenMessage
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.SampleDataBanner
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.MonitorPreviews
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.NetworkStatsUiState
import com.aoscoremonitor.ui.viewmodel.NetworkStatsViewModel

@Composable
fun NetworkStatsScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier, viewModel: NetworkStatsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NetworkStatsContent(uiState = uiState, onNavigateBack = onNavigateBack, modifier = modifier)
}

@Composable
private fun NetworkStatsContent(uiState: NetworkStatsUiState, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val interfaces = remember(uiState.stats) { uiState.stats.value.entries.toList() }

    MonitorScaffold(
        title = stringResource(R.string.network_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) { innerPadding ->
        if (interfaces.isEmpty()) {
            FullScreenMessage(
                message = stringResource(
                    if (uiState.hasLoaded) R.string.network_empty else R.string.network_loading
                ),
                icon = Icons.Default.NetworkCell,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(Spacing.Large),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                if (uiState.stats.isSample) {
                    item(key = "sample") { SampleDataBanner(stringResource(R.string.network_sample)) }
                }
                items(interfaces, key = { it.key }) { (name, stats) ->
                    NetworkInterfaceCard(interfaceName = name, stats = stats)
                }
            }
        }
    }
}

@Composable
private fun NetworkInterfaceCard(interfaceName: String, stats: NativeSystemMonitor.InterfaceStats, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        Text(text = interfaceName, style = MaterialTheme.typography.titleMedium)

        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.Small))

        TransferRow(
            icon = Icons.Default.Download,
            label = stringResource(R.string.network_received),
            total = stats.getFormattedRxBytes(),
            detail = stringResource(
                R.string.network_packet_detail,
                stats.rxPackets,
                stats.rxErrors,
                stats.rxDropped
            )
        )
        TransferRow(
            icon = Icons.Default.Upload,
            label = stringResource(R.string.network_transmitted),
            total = stats.getFormattedTxBytes(),
            detail = stringResource(
                R.string.network_packet_detail,
                stats.txPackets,
                stats.txErrors,
                stats.txDropped
            )
        )
    }
}

/**
 * One direction of transfer.
 *
 * The card used to close with a "Total Transfer" line restating both directions' byte counts, one
 * line after each had already been shown. It said nothing new, so it is gone.
 */
@Composable
private fun TransferRow(icon: ImageVector, label: String, total: String, detail: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.padding(start = Spacing.Medium)) {
            Text(text = "$label · $total", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@MonitorPreviews
@Composable
private fun NetworkStatsPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        NetworkStatsContent(
            uiState = NetworkStatsUiState(
                stats = Collected.sample(
                    mapOf(
                        "wlan0" to NativeSystemMonitor.InterfaceStats(
                            rxBytes = 148_312_064,
                            rxPackets = 120_431,
                            rxErrors = 0,
                            rxDropped = 12,
                            txBytes = 24_115_200,
                            txPackets = 88_210,
                            txErrors = 0,
                            txDropped = 0
                        )
                    )
                ),
                hasLoaded = true
            ),
            onNavigateBack = {}
        )
    }
}
