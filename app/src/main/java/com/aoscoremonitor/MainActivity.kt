package com.aoscoremonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
                    LogDisplay(
                        logs = logLines,
                        modifier = Modifier.padding(innerPadding)
                    )
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
fun LogDisplay(logs: List<String>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        items(logs) { log ->
            Text(text = log)
        }
    }
}
