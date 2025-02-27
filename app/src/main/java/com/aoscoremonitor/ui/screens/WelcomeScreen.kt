package com.aoscoremonitor.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WelcomeScreen(
    onNavigateToLogs: () -> Unit,
    onNavigateToSystemInfo: () -> Unit,
    onNavigateToSystemDiagnostics: () -> Unit,
    onNavigateToSecurityInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onNavigateToSystemInfo,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = "System Information")
        }

        Button(
            onClick = onNavigateToLogs,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = "View Logs")
        }

        Button(
            onClick = onNavigateToSystemDiagnostics,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = "System Diagnostics")
        }

        Button(
            onClick = onNavigateToSecurityInfo,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = "Security Analysis")
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}
