package com.aoscoremonitor.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.aoscoremonitor.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * A screen the user can be on.
 *
 * These are Navigation 3 keys: the back stack holds them rather than the screens themselves, and
 * because they are [Serializable] the stack survives rotation and process death. The previous
 * `enum class Screen` could not do that — it was paired with a `remember`ed field, so rotating
 * the device dropped the user back on the home screen.
 *
 * Each key is named after the screen it opens, so `Destination.SystemDiagnostics` leads to
 * `SystemDiagnosticsScreen` and the pairing needs no lookup. They are nested rather than
 * top-level because several of the names they need are taken: a top-level `Security` shadowed
 * `Icons.Filled.Security` in this very file, and `Log` would shadow `android.util.Log`.
 *
 * [titleRes] and [icon] hang off the key so the app bar title and the home grid both read them
 * from one place instead of repeating the label at each call site. Neither is serialized.
 */
@Serializable
sealed interface Destination : NavKey {
    @get:StringRes
    val titleRes: Int

    val icon: ImageVector

    @Serializable
    data object Home : Destination {
        override val titleRes get() = R.string.app_name

        @Transient
        override val icon: ImageVector = Icons.Default.Computer
    }

    @Serializable
    data object SystemInfo : Destination {
        override val titleRes get() = R.string.system_info_title

        @Transient
        override val icon: ImageVector = Icons.Default.Computer
    }

    @Serializable
    data object Log : Destination {
        override val titleRes get() = R.string.logs_title

        @Transient
        override val icon: ImageVector = Icons.AutoMirrored.Filled.List
    }

    @Serializable
    data object SystemDiagnostics : Destination {
        override val titleRes get() = R.string.diagnostics_title

        @Transient
        override val icon: ImageVector = Icons.Default.BarChart
    }

    @Serializable
    data object SecurityInfo : Destination {
        override val titleRes get() = R.string.security_title

        @Transient
        override val icon: ImageVector = Icons.Default.Security
    }

    @Serializable
    data object FrameworkAnalysis : Destination {
        override val titleRes get() = R.string.framework_title

        @Transient
        override val icon: ImageVector = Icons.Default.Analytics
    }

    @Serializable
    data object HalInfo : Destination {
        override val titleRes get() = R.string.hal_title

        @Transient
        override val icon: ImageVector = Icons.Default.Settings
    }

    @Serializable
    data object Sensors : Destination {
        override val titleRes get() = R.string.sensors_title

        @Transient
        override val icon: ImageVector = Icons.Default.Sensors
    }

    @Serializable
    data object Threads : Destination {
        override val titleRes get() = R.string.threads_title

        @Transient
        override val icon: ImageVector = Icons.Default.AccountTree
    }

    @Serializable
    data object KernelCounters : Destination {
        override val titleRes get() = R.string.kernel_title

        @Transient
        override val icon: ImageVector = Icons.Default.Memory
    }

    @Serializable
    data object NetworkStats : Destination {
        override val titleRes get() = R.string.network_title

        @Transient
        override val icon: ImageVector = Icons.Default.NetworkCell
    }

    @Serializable
    data object TcpConnections : Destination {
        override val titleRes get() = R.string.tcp_title

        @Transient
        override val icon: ImageVector = Icons.Default.Cloud
    }

    @Serializable
    data object CpuCores : Destination {
        override val titleRes get() = R.string.cpu_title

        @Transient
        override val icon: ImageVector = Icons.Default.DeveloperBoard
    }

    @Serializable
    data object MemoryMap : Destination {
        override val titleRes get() = R.string.memory_title

        @Transient
        override val icon: ImageVector = Icons.Default.DataUsage
    }

    @Serializable
    data object LoadedLibraries : Destination {
        override val titleRes get() = R.string.libraries_title

        @Transient
        override val icon: ImageVector = Icons.Default.Layers
    }

    @Serializable
    data object StorageMounts : Destination {
        override val titleRes get() = R.string.storage_title

        @Transient
        override val icon: ImageVector = Icons.Default.Storage
    }

    @Serializable
    data object Descriptors : Destination {
        override val titleRes get() = R.string.descriptors_title

        @Transient
        override val icon: ImageVector = Icons.Default.Description
    }

    @Serializable
    data object ProcessCredentials : Destination {
        override val titleRes get() = R.string.credentials_title

        @Transient
        override val icon: ImageVector = Icons.Default.VerifiedUser
    }
}

/**
 * What the home screen offers, in the order it offers it.
 *
 * Grouped by layer — the Android framework first, then the native and network views underneath —
 * rather than the arbitrary order the grid grew in. The four screens added last read the CPU,
 * the process's own address space, the linker and the mount table, so they continue the native
 * run rather than starting a group of their own.
 *
 * Within the native run the order goes outwards from the process: what it is running (CPU,
 * threads), what it is holding (memory, libraries, descriptors), who it is (credentials), and what
 * it can reach (storage, network).
 */
val HomeDestinations: List<Destination> = listOf(
    Destination.SystemInfo,
    Destination.Log,
    Destination.SystemDiagnostics,
    Destination.SecurityInfo,
    Destination.FrameworkAnalysis,
    Destination.HalInfo,
    Destination.Sensors,
    Destination.KernelCounters,
    Destination.CpuCores,
    Destination.Threads,
    Destination.MemoryMap,
    Destination.LoadedLibraries,
    Destination.Descriptors,
    Destination.ProcessCredentials,
    Destination.StorageMounts,
    Destination.NetworkStats,
    Destination.TcpConnections
)

/** The short label the home grid uses, which is not always the app bar title. */
@get:StringRes
val Destination.shortTitleRes: Int
    get() = when (this) {
        Destination.Home -> R.string.app_name
        Destination.SystemInfo -> R.string.destination_system_info
        Destination.Log -> R.string.destination_logs
        Destination.SystemDiagnostics -> R.string.destination_diagnostics
        Destination.SecurityInfo -> R.string.destination_security
        Destination.FrameworkAnalysis -> R.string.destination_framework
        Destination.HalInfo -> R.string.destination_hal
        Destination.Sensors -> R.string.destination_sensors
        Destination.Threads -> R.string.destination_threads
        Destination.KernelCounters -> R.string.destination_kernel_counters
        Destination.NetworkStats -> R.string.destination_network_stats
        Destination.TcpConnections -> R.string.destination_tcp_connections
        Destination.CpuCores -> R.string.destination_cpu_cores
        Destination.MemoryMap -> R.string.destination_memory_map
        Destination.LoadedLibraries -> R.string.destination_loaded_libraries
        Destination.StorageMounts -> R.string.destination_storage
        Destination.Descriptors -> R.string.destination_descriptors
        Destination.ProcessCredentials -> R.string.destination_credentials
    }
