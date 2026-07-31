package com.aoscoremonitor.ui.screens.jni

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aoscoremonitor.diagnostics.Collected
import com.aoscoremonitor.diagnostics.jni.NativeSystemMonitor
import com.aoscoremonitor.ui.components.SampleDataBanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TcpConnectionsScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var tcpConnections by remember {
        mutableStateOf(Collected.real(emptyList<NativeSystemMonitor.TcpConnection>()))
    }
    var refreshing by remember { mutableStateOf(false) }

    val systemMonitor = remember { NativeSystemMonitor() }

    // Periodically update information
    LaunchedEffect(Unit) {
        while (isActive) {
            refreshing = true
            tcpConnections = systemMonitor.getTcpConnections()
            refreshing = false
            delay(3000) // Update every 3 seconds
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TCP Connections") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (tcpConnections.value.isEmpty()) {
                EmptyConnectionsView(refreshing)
            } else {
                if (tcpConnections.isSample) {
                    SampleDataBanner("Sample data: /proc/net/tcp is not readable by apps")
                }
                ConnectionStatusSummary(tcpConnections.value)
                TcpConnectionsList(tcpConnections.value, tcpConnections.isSample)
            }
        }
    }
}

@Composable
private fun ConnectionStatusSummary(connections: List<NativeSystemMonitor.TcpConnection>) {
    val established = connections.count { it.status == "ESTABLISHED" }
    val listening = connections.count { it.status == "LISTEN" }
    val waiting = connections.count { it.status == "TIME_WAIT" || it.status == "CLOSE_WAIT" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = established.toString(), style = MaterialTheme.typography.titleLarge)
                Text(text = "Established", style = MaterialTheme.typography.bodyMedium)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = listening.toString(), style = MaterialTheme.typography.titleLarge)
                Text(text = "Listening", style = MaterialTheme.typography.bodyMedium)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = waiting.toString(), style = MaterialTheme.typography.titleLarge)
                Text(text = "Waiting", style = MaterialTheme.typography.bodyMedium)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = connections.size.toString(), style = MaterialTheme.typography.titleLarge)
                Text(text = "Total", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TcpConnectionsList(connections: List<NativeSystemMonitor.TcpConnection>, isSample: Boolean) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        items(connections) { connection ->
            TcpConnectionItem(connection, isSample)
        }
    }
}

@Composable
private fun TcpConnectionItem(connection: NativeSystemMonitor.TcpConnection, isSample: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (isSample) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Status row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSample) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Sample data",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                val (icon, color) = when (connection.status) {
                    "ESTABLISHED" -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
                    "LISTEN" -> Icons.Default.Hearing to MaterialTheme.colorScheme.tertiary
                    "TIME_WAIT", "CLOSE_WAIT" -> Icons.Default.Timer to MaterialTheme.colorScheme.error
                    else -> Icons.Default.Info to MaterialTheme.colorScheme.onSurface
                }

                Icon(
                    imageVector = icon,
                    contentDescription = connection.status,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )

                Text(
                    text = connection.status,
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Address information
            Text(
                text = "Local: ${connection.getFormattedLocalAddress()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                text = "Remote: ${connection.getFormattedRemoteAddress()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            // UID information (app identification)
            Text(
                text = "UID: ${connection.uid}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun EmptyConnectionsView(refreshing: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (refreshing) "Loading TCP connections..." else "No active TCP connections found",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}
