package com.aoscoremonitor.ui.screens.jni

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aoscoremonitor.diagnostics.jni.NativeSystemMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TcpConnectionsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tcpConnections by remember { mutableStateOf<List<NativeSystemMonitor.TcpConnection>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var isDummyData by remember { mutableStateOf(false) }

    val systemMonitor = remember { NativeSystemMonitor() }

    // Periodically update information
    LaunchedEffect(Unit) {
        while (isActive) {
            refreshing = true
            tcpConnections = systemMonitor.getTcpConnections()

            // Check if this is dummy data by comparing with known dummy data patterns
            // This is a simple heuristic - we check if the data contains the exact local addresses from dummy data
            isDummyData = tcpConnections.any { conn ->
                conn.localAddress == "0100007F:1F90" || // 127.0.0.1:8080
                    conn.localAddress == "0100007F:01BB" || // 127.0.0.1:443
                    conn.localAddress == "0100007F:0050" || // 127.0.0.1:80
                    conn.localAddress == "78563412:0CEA" // 18.52.86.120:3338
            }

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
                            imageVector = Icons.Filled.ArrowBack,
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
            if (tcpConnections.isEmpty()) {
                EmptyConnectionsView(refreshing)
            } else {
                if (isDummyData) {
                    DummyDataBanner()
                }
                ConnectionStatusSummary(tcpConnections)
                TcpConnectionsList(tcpConnections, isDummyData)
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
private fun DummyDataBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Displaying dummy TCP connection data",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun TcpConnectionsList(connections: List<NativeSystemMonitor.TcpConnection>, isDummyData: Boolean) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        items(connections) { connection ->
            TcpConnectionItem(connection, isDummyData)
        }
    }
}

@Composable
private fun TcpConnectionItem(connection: NativeSystemMonitor.TcpConnection, isDummyData: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (isDummyData) {
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
                if (isDummyData) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Dummy Data",
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
