package com.aoscoremonitor.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
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
 * [titleRes] and [icon] hang off the key so the app bar title and the home grid both read them
 * from one place instead of repeating the label at each call site. Neither is serialized.
 */
@Serializable
sealed interface Destination : NavKey {
    @get:StringRes
    val titleRes: Int

    val icon: ImageVector
}

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
data object Logs : Destination {
    override val titleRes get() = R.string.logs_title

    @Transient
    override val icon: ImageVector = Icons.AutoMirrored.Filled.List
}

@Serializable
data object Diagnostics : Destination {
    override val titleRes get() = R.string.diagnostics_title

    @Transient
    override val icon: ImageVector = Icons.Default.BarChart
}

@Serializable
data object Security : Destination {
    override val titleRes get() = R.string.security_title

    @Transient
    override val icon: ImageVector = Icons.Default.Security
}

@Serializable
data object Framework : Destination {
    override val titleRes get() = R.string.framework_title

    @Transient
    override val icon: ImageVector = Icons.Default.Analytics
}

@Serializable
data object Hal : Destination {
    override val titleRes get() = R.string.hal_title

    @Transient
    override val icon: ImageVector = Icons.Default.Settings
}

@Serializable
data object NativeMonitor : Destination {
    override val titleRes get() = R.string.native_title

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

/**
 * What the home screen offers, in the order it offers it.
 *
 * Grouped by layer — the Android framework first, then the native and network views underneath —
 * rather than the arbitrary order the grid grew in.
 */
val HomeDestinations: List<Destination> = listOf(
    SystemInfo,
    Logs,
    Diagnostics,
    Security,
    Framework,
    Hal,
    NativeMonitor,
    NetworkStats,
    TcpConnections
)

/** The short label the home grid uses, which is not always the app bar title. */
@get:StringRes
val Destination.menuTitleRes: Int
    get() = when (this) {
        Home -> R.string.app_name
        SystemInfo -> R.string.destination_system_info
        Logs -> R.string.destination_logs
        Diagnostics -> R.string.destination_diagnostics
        Security -> R.string.destination_security
        Framework -> R.string.destination_framework
        Hal -> R.string.destination_hal
        NativeMonitor -> R.string.destination_native_monitor
        NetworkStats -> R.string.destination_network_stats
        TcpConnections -> R.string.destination_tcp_connections
    }
