package com.aoscoremonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aoscoremonitor.diagnostics.LogCollector
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme

class MainActivity : ComponentActivity() {
    private lateinit var logCollector: LogCollector
    private val logLines = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize LogCollector
        logCollector = LogCollector { line ->
            logLines.add(line)
        }

        enableEdgeToEdge()
        setContent {
            AOSCoreMonitorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var showLogDisplay by remember { mutableStateOf(false) }

                    if (!showLogDisplay) {
                        WelcomeScreen(
                            onNavigateToLogs = { showLogDisplay = true },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        LogDisplay(
                            logs = logLines,
                            onNavigateBack = { showLogDisplay = false },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        logCollector.startCollecting()
    }

    override fun onPause() {
        super.onPause()
        logCollector.stopCollecting()
    }
}

@Composable
fun WelcomeScreen(onNavigateToLogs: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onNavigateToLogs,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = "Hello Android!")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDisplay(
    logs: List<String>,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
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
        LazyColumn(modifier = modifier.padding(innerPadding)) {
            items(logs) { log ->
                Text(
                    text = log,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}
