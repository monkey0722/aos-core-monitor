package com.aoscoremonitor.ui.screens.jni

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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aoscoremonitor.diagnostics.jni.NativeSystemMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeSystemMonitorScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var cpuInfo by remember { mutableStateOf(mapOf<String, Long>()) }
    var memInfo by remember { mutableStateOf(mapOf<String, Long>()) }
    var currentProcessInfo by remember { mutableStateOf(mapOf<String, String>()) }

    val systemMonitor = remember { NativeSystemMonitor() }

    // Update information periodically
    LaunchedEffect(Unit) {
        while (isActive) {
            cpuInfo = systemMonitor.getCpuInfo()
            memInfo = systemMonitor.getMemInfo()
            currentProcessInfo = systemMonitor.getProcessInfo(android.os.Process.myPid())
            delay(1000) // Update every second
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Native System Monitor") },
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
                .verticalScroll(scrollState)
        ) {
            // CPU Information Section
            SectionHeader("CPU Information")
            cpuInfo.forEach { (key, value) ->
                InfoItem(key, value.toString())
            }

            // Memory Information Section
            SectionHeader("Memory Information")
            memInfo.forEach { (key, value) ->
                InfoItem(key, "$value kB")
            }

            // Current Process Information Section
            SectionHeader("Current Process Information")
            currentProcessInfo.forEach { (key, value) ->
                InfoItem(key, value)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun InfoItem(key: String, value: String) {
    Text(
        text = "$key: $value",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
    )
}
