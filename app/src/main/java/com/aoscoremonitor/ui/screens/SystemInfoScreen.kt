package com.aoscoremonitor.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aoscoremonitor.diagnostics.SystemInfoCollector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemInfoScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var systemInfo by remember {
        mutableStateOf(
            SystemInfoCollector.SystemInfo(
                cpuUsage = "CPU: N/A",
                memoryUsage = "Memory: N/A",
                batteryStatus = "Battery: N/A",
                networkStatus = "Network: N/A"
            )
        )
    }

    val systemInfoCollector = remember {
        SystemInfoCollector(context) { info ->
            systemInfo = info
        }
    }

    DisposableEffect(systemInfoCollector) {
        systemInfoCollector.startCollecting()
        onDispose {
            systemInfoCollector.stopCollecting()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Information") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            InfoItem(title = "CPU Usage", value = systemInfo.cpuUsage)
            InfoItem(title = "Memory", value = systemInfo.memoryUsage)
            InfoItem(title = "Battery", value = systemInfo.batteryStatus)
            InfoItem(title = "Network", value = systemInfo.networkStatus)
        }
    }
}

@Composable
private fun InfoItem(title: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
