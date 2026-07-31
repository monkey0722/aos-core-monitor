package com.aoscoremonitor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.aoscoremonitor.ui.screens.FrameworkAnalysisScreen
import com.aoscoremonitor.ui.screens.HalInfoScreen
import com.aoscoremonitor.ui.screens.HomeScreen
import com.aoscoremonitor.ui.screens.LogScreen
import com.aoscoremonitor.ui.screens.SecurityInfoScreen
import com.aoscoremonitor.ui.screens.SystemDiagnosticsScreen
import com.aoscoremonitor.ui.screens.SystemInfoScreen
import com.aoscoremonitor.ui.screens.jni.NativeSystemMonitorScreen
import com.aoscoremonitor.ui.screens.jni.NetworkStatsScreen
import com.aoscoremonitor.ui.screens.jni.TcpConnectionsScreen

/**
 * The app's single navigation host.
 *
 * What this replaces is a `when` over an enum held in a `remember`ed field. That arrangement had
 * three problems this fixes:
 *
 * - The system back gesture was never handled, so backing out of a detail screen closed the app
 *   instead of returning here. [NavDisplay] handles back, including the predictive-back preview.
 * - The current screen was not saved, so a rotation returned the user to the home grid.
 *   [rememberNavBackStack] persists the stack across configuration changes and process death.
 * - Screens took nine separate `onNavigateTo…` callbacks threaded down from the activity. They
 *   now push a [Destination] onto the stack.
 *
 * [rememberViewModelStoreNavEntryDecorator] is what scopes each screen's view models to its back
 * stack entry, so a screen's collectors are disposed when it is popped rather than living as long
 * as the activity.
 */
@Composable
fun MonitorNavHost(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Destination.Home)
    val goBack: () -> Unit = { backStack.removeLastOrNull() }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = goBack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = remember(backStack) {
            entryProvider {
                entry<Destination.Home> { HomeScreen(onNavigate = { backStack.add(it) }) }
                entry<Destination.SystemInfo> { SystemInfoScreen(onNavigateBack = goBack) }
                entry<Destination.Log> { LogScreen(onNavigateBack = goBack) }
                entry<Destination.SystemDiagnostics> { SystemDiagnosticsScreen(onNavigateBack = goBack) }
                entry<Destination.SecurityInfo> { SecurityInfoScreen(onNavigateBack = goBack) }
                entry<Destination.FrameworkAnalysis> { FrameworkAnalysisScreen(onNavigateBack = goBack) }
                entry<Destination.HalInfo> { HalInfoScreen(onNavigateBack = goBack) }
                entry<Destination.NativeSystemMonitor> { NativeSystemMonitorScreen(onNavigateBack = goBack) }
                entry<Destination.NetworkStats> { NetworkStatsScreen(onNavigateBack = goBack) }
                entry<Destination.TcpConnections> { TcpConnectionsScreen(onNavigateBack = goBack) }
            }
        }
    )
}
