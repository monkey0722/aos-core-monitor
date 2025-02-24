package com.aoscoremonitor.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.aoscoremonitor.diagnostics.SystemDiagnosticsCollector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemDiagnosticsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var diagnosticsInfo by remember {
        mutableStateOf(
            SystemDiagnosticsCollector.DiagnosticsInfo(
                runningProcesses = emptyList(),
                availableMemory = "Memory: N/A",
                screenOn = false,
                dumpsysResult = "Loading..."
            )
        )
    }

    val diagnosticsCollector = remember {
        SystemDiagnosticsCollector(context) { info ->
            diagnosticsInfo = info
        }
    }

    DisposableEffect(diagnosticsCollector) {
        diagnosticsCollector.startCollecting()
        onDispose {
            diagnosticsCollector.stopCollecting()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Diagnostics") },
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
                .verticalScroll(scrollState)
        ) {
            InfoSection(title = "Memory", value = diagnosticsInfo.availableMemory)

            InfoSection(
                title = "Screen State",
                value = if (diagnosticsInfo.screenOn) "On" else "Off"
            )

            InfoSection(
                title = "Running Processes",
                value = diagnosticsInfo.runningProcesses.joinToString("\n")
            )

            InfoSection(
                title = "Dumpsys Memory Info",
                value = diagnosticsInfo.dumpsysResult
            )
        }
    }
}

@Composable
private fun InfoSection(title: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
