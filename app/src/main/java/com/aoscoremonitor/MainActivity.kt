package com.aoscoremonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.aoscoremonitor.diagnostics.LogCollector
import com.aoscoremonitor.ui.navigation.Screen
import com.aoscoremonitor.ui.screens.FrameworkAnalysisScreen
import com.aoscoremonitor.ui.screens.HalInfoScreen
import com.aoscoremonitor.ui.screens.LogDisplay
import com.aoscoremonitor.ui.screens.SecurityInfoScreen
import com.aoscoremonitor.ui.screens.SystemDiagnosticsScreen
import com.aoscoremonitor.ui.screens.SystemInfoScreen
import com.aoscoremonitor.ui.screens.WelcomeScreen
import com.aoscoremonitor.ui.screens.jni.NativeSystemMonitorScreen
import com.aoscoremonitor.ui.screens.jni.NetworkStatsScreen
import com.aoscoremonitor.ui.screens.jni.TcpConnectionsScreen
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
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.Welcome) }

                    when (currentScreen) {
                        Screen.Welcome -> {
                            WelcomeScreen(
                                onNavigateToLogs = { currentScreen = Screen.Logs },
                                onNavigateToSystemInfo = { currentScreen = Screen.SystemInfo },
                                onNavigateToSystemDiagnostics = { currentScreen = Screen.SystemDiagnostics },
                                onNavigateToSecurityInfo = { currentScreen = Screen.SecurityInfo },
                                onNavigateToFrameworkAnalysis = { currentScreen = Screen.FrameworkAnalysis },
                                onNavigateToHalInfo = { currentScreen = Screen.HalInfo },
                                onNavigateToNativeSystemMonitor = { currentScreen = Screen.NativeSystemMonitor },
                                onNavigateToNetworkStats = { currentScreen = Screen.NetworkStats },
                                onNavigateToTcpConnections = { currentScreen = Screen.TcpConnections },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        Screen.Logs -> {
                            LogDisplay(
                                logs = logLines,
                                onNavigateBack = { currentScreen = Screen.Welcome },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        Screen.SystemInfo -> {
                            SystemInfoScreen(
                                onNavigateBack = { currentScreen = Screen.Welcome },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        Screen.SystemDiagnostics -> {
                            SystemDiagnosticsScreen(
                                onNavigateBack = { currentScreen = Screen.Welcome },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        Screen.SecurityInfo -> {
                            SecurityInfoScreen(
                                onNavigateBack = { currentScreen = Screen.Welcome },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        Screen.FrameworkAnalysis -> {
                            FrameworkAnalysisScreen(
                                onNavigateBack = { currentScreen = Screen.Welcome },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        Screen.HalInfo -> {
                            HalInfoScreen(
                                onNavigateBack = { currentScreen = Screen.Welcome },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        Screen.NativeSystemMonitor -> {
                            NativeSystemMonitorScreen(
                                onNavigateBack = { currentScreen = Screen.Welcome },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        Screen.NetworkStats -> {
                            NetworkStatsScreen(
                                onNavigateBack = { currentScreen = Screen.Welcome },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        Screen.TcpConnections -> {
                            TcpConnectionsScreen(
                                onNavigateBack = { currentScreen = Screen.Welcome },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
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
