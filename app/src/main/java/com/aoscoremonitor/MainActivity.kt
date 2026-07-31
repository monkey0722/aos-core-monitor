package com.aoscoremonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aoscoremonitor.ui.navigation.MonitorNavDisplay
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme

/**
 * Hosts the whole app.
 *
 * The activity used to own the screen-selection state, the log collector and a `Scaffold` wrapped
 * around every screen — which, since each screen supplies its own, meant window insets were
 * applied twice and left a gap under the app bar. All three now belong to the components that
 * actually need them: navigation state to [MonitorNavDisplay], log collection to the log screen's
 * view model, and the scaffold to each screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AOSCoreMonitorTheme {
                MonitorNavDisplay()
            }
        }
    }
}
