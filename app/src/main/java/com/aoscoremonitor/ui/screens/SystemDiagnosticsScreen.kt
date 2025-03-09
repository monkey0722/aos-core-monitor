package com.aoscoremonitor.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            // Memory Card
            DiagnosticCard(
                title = "Memory",
                value = diagnosticsInfo.availableMemory,
                icon = Icons.Default.Memory,
                color = MaterialTheme.colorScheme.primary
            )

            // Screen State Card
            DiagnosticCard(
                title = "Screen State",
                value = if (diagnosticsInfo.screenOn) "On" else "Off",
                icon = if (diagnosticsInfo.screenOn) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                color = if (diagnosticsInfo.screenOn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
            )

            // Running Processes Card
            DiagnosticCard(
                title = "Running Processes",
                value = if (diagnosticsInfo.runningProcesses.isEmpty()) {
                    "No process information available"
                } else {
                    diagnosticsInfo.runningProcesses.joinToString("\n")
                },
                icon = Icons.Default.List,
                color = MaterialTheme.colorScheme.error,
                expandable = true,
                maxHeight = 200.dp
            )

            // Dumpsys Result Card
            DiagnosticCard(
                title = "Dumpsys Memory Info",
                value = diagnosticsInfo.dumpsysResult,
                icon = Icons.Default.Terminal,
                color = MaterialTheme.colorScheme.secondary,
                expandable = true,
                maxHeight = 300.dp
            )
        }
    }
}

@Composable
private fun DiagnosticCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    expandable: Boolean = false,
    maxHeight: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(28.dp)
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            if (expandable) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(maxHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
