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
import com.aoscoremonitor.ui.screens.SensorsScreen
import com.aoscoremonitor.ui.screens.SystemDiagnosticsScreen
import com.aoscoremonitor.ui.screens.SystemInfoScreen
import com.aoscoremonitor.ui.screens.jni.CpuCoresScreen
import com.aoscoremonitor.ui.screens.jni.DescriptorsScreen
import com.aoscoremonitor.ui.screens.jni.KernelCountersScreen
import com.aoscoremonitor.ui.screens.jni.LoadedLibrariesScreen
import com.aoscoremonitor.ui.screens.jni.MemoryMapScreen
import com.aoscoremonitor.ui.screens.jni.NetworkStatsScreen
import com.aoscoremonitor.ui.screens.jni.ProcessCredentialsScreen
import com.aoscoremonitor.ui.screens.jni.StorageMountsScreen
import com.aoscoremonitor.ui.screens.jni.TcpConnectionsScreen
import com.aoscoremonitor.ui.screens.jni.ThreadsScreen

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

    // Guarded because [NavDisplay] throws IllegalArgumentException — "backstack cannot be empty" —
    // the moment the last entry goes, and this app was seen to crash with exactly that message.
    //
    // The trigger was not reproduced: system back, a double back, the edge-swipe gesture, rotation
    // and process death were all tried against the unguarded version without emptying the stack,
    // because NavDisplay does not route back here once Home is the only entry. So this does not
    // fix a known path; it makes the state the crash reported unreachable from this callback,
    // which is one comparison.
    //
    // What it must not do is turn back at the root into a no-op, leaving the user unable to leave
    // the app. MonitorNavigationTest.backingOutPastHomeLeavesTheApp pins that.
    val goBack: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() }

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
                entry<Destination.Sensors> { SensorsScreen(onNavigateBack = goBack) }
                entry<Destination.Threads> { ThreadsScreen(onNavigateBack = goBack) }
                entry<Destination.KernelCounters> { KernelCountersScreen(onNavigateBack = goBack) }
                entry<Destination.NetworkStats> { NetworkStatsScreen(onNavigateBack = goBack) }
                entry<Destination.TcpConnections> { TcpConnectionsScreen(onNavigateBack = goBack) }
                entry<Destination.CpuCores> { CpuCoresScreen(onNavigateBack = goBack) }
                entry<Destination.MemoryMap> { MemoryMapScreen(onNavigateBack = goBack) }
                entry<Destination.LoadedLibraries> { LoadedLibrariesScreen(onNavigateBack = goBack) }
                entry<Destination.StorageMounts> { StorageMountsScreen(onNavigateBack = goBack) }
                entry<Destination.Descriptors> { DescriptorsScreen(onNavigateBack = goBack) }
                entry<Destination.ProcessCredentials> { ProcessCredentialsScreen(onNavigateBack = goBack) }
            }
        }
    )
}
