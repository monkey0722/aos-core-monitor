package com.aoscoremonitor.ui.screens.jni

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aoscoremonitor.R
import com.aoscoremonitor.diagnostics.Collected
import com.aoscoremonitor.diagnostics.jni.NativeSystemMonitor
import com.aoscoremonitor.ui.components.FullScreenMessage
import com.aoscoremonitor.ui.components.LabeledValue
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.ReadingStatus
import com.aoscoremonitor.ui.components.SampleDataBanner
import com.aoscoremonitor.ui.components.StatusRow
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.MonitorPreviews
import com.aoscoremonitor.ui.theme.MonitorTypography
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.TcpConnectionsUiState
import com.aoscoremonitor.ui.viewmodel.TcpConnectionsViewModel

@Composable
fun TcpConnectionsScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier, viewModel: TcpConnectionsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TcpConnectionsContent(uiState = uiState, onNavigateBack = onNavigateBack, modifier = modifier)
}

@Composable
private fun TcpConnectionsContent(uiState: TcpConnectionsUiState, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val connections = uiState.connections.value

    MonitorScaffold(
        title = stringResource(R.string.tcp_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) { innerPadding ->
        if (connections.isEmpty()) {
            FullScreenMessage(
                message = stringResource(if (uiState.hasLoaded) R.string.tcp_empty else R.string.tcp_loading),
                icon = Icons.Default.CloudOff,
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
                if (uiState.connections.isSample) {
                    item(key = "sample") { SampleDataBanner(stringResource(R.string.tcp_sample)) }
                }
                item(key = "summary") { ConnectionSummary(uiState) }
                // The inode is the kernel's own identifier for the socket, so it keys the row
                // stably even as addresses churn between polls.
                items(connections, key = { it.inode }) { connection ->
                    TcpConnectionCard(connection)
                }
            }
        }
    }
}

/**
 * The four counts at the top of the screen.
 *
 * Previously the summary sat outside the list, so it stayed pinned while the connections scrolled
 * under it and ate a fixed slice of a phone screen. It scrolls with the list now.
 */
@Composable
private fun ConnectionSummary(uiState: TcpConnectionsUiState, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryCount(uiState.established, stringResource(R.string.tcp_established))
            SummaryCount(uiState.listening, stringResource(R.string.tcp_listening))
            SummaryCount(uiState.waiting, stringResource(R.string.tcp_waiting))
            SummaryCount(uiState.total, stringResource(R.string.tcp_total))
        }
    }
}

@Composable
private fun SummaryCount(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count.toString(), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TcpConnectionCard(connection: NativeSystemMonitor.TcpConnection, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        StatusRow(
            label = connection.status,
            status = connection.readingStatus,
            statusDescription = connection.status
        )
        LabeledValue(
            label = stringResource(R.string.tcp_local),
            value = connection.getFormattedLocalAddress(),
            valueStyle = MonitorTypography.machineText
        )
        LabeledValue(
            label = stringResource(R.string.tcp_remote),
            value = connection.getFormattedRemoteAddress(),
            valueStyle = MonitorTypography.machineText
        )
        LabeledValue(label = stringResource(R.string.tcp_uid), value = connection.uid.toString())
    }
}

/**
 * A socket state, read as health.
 *
 * TIME_WAIT and CLOSE_WAIT used to be drawn in the error color, which overstates them — they are
 * ordinary states in a connection's teardown, not failures.
 */
private val NativeSystemMonitor.TcpConnection.readingStatus: ReadingStatus
    get() = when (status) {
        "ESTABLISHED" -> ReadingStatus.Ok
        "LISTEN" -> ReadingStatus.Neutral
        "TIME_WAIT", "CLOSE_WAIT" -> ReadingStatus.Warning
        else -> ReadingStatus.Neutral
    }

@MonitorPreviews
@Composable
private fun TcpConnectionsPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        TcpConnectionsContent(
            uiState = TcpConnectionsUiState(
                connections = Collected.sample(
                    listOf(
                        NativeSystemMonitor.TcpConnection(
                            localAddress = "0100007F:1F90",
                            remoteAddress = "00000000:0000",
                            status = "LISTEN",
                            uid = 1000,
                            inode = "31427"
                        ),
                        NativeSystemMonitor.TcpConnection(
                            localAddress = "0A00020F:B3C2",
                            remoteAddress = "8EFAB48E:01BB",
                            status = "ESTABLISHED",
                            uid = 10123,
                            inode = "31892"
                        )
                    )
                ),
                hasLoaded = true
            ),
            onNavigateBack = {}
        )
    }
}
