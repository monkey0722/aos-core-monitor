package com.aoscoremonitor.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aoscoremonitor.ui.theme.statusColors

/**
 * What a reading says about the thing it describes.
 *
 * Screens used to pick a color role per reading — `tertiary` for one warning, `error` for
 * another, `inversePrimary` for an icon that then failed contrast against its own card. Naming
 * the state instead means the same state looks the same everywhere, and the mapping to color and
 * icon lives in one place.
 */
enum class ReadingStatus {
    /** Working as intended: a HAL is running, a security feature is backed by hardware. */
    Ok,

    /** Working, but not the preferred configuration: SELinux permissive, sample data in use. */
    Warning,

    /** Not working, or a finding worth acting on: a stopped HAL, a dangerous permission. */
    Problem,

    /** No judgement — plain information. */
    Neutral
}

/** The color that carries [ReadingStatus] on a surface. */
val ReadingStatus.color: Color
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        ReadingStatus.Ok -> MaterialTheme.statusColors.ok
        ReadingStatus.Warning -> MaterialTheme.statusColors.warning
        ReadingStatus.Problem -> MaterialTheme.colorScheme.error
        ReadingStatus.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }

/**
 * The container color for a card whose whole subject is in this state.
 *
 * [ReadingStatus.Neutral] keeps the default card color, so callers pass it through rather than
 * special-casing.
 */
val ReadingStatus.containerColor: Color
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        ReadingStatus.Ok -> MaterialTheme.statusColors.okContainer
        ReadingStatus.Warning -> MaterialTheme.statusColors.warningContainer
        ReadingStatus.Problem -> MaterialTheme.colorScheme.errorContainer
        ReadingStatus.Neutral -> MaterialTheme.colorScheme.surfaceContainerLow
    }

/** The text color that reads on [containerColor]. */
val ReadingStatus.onContainerColor: Color
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        ReadingStatus.Ok -> MaterialTheme.statusColors.onOkContainer
        ReadingStatus.Warning -> MaterialTheme.statusColors.onWarningContainer
        ReadingStatus.Problem -> MaterialTheme.colorScheme.onErrorContainer
        ReadingStatus.Neutral -> MaterialTheme.colorScheme.onSurface
    }

/**
 * The icon that stands in for [ReadingStatus].
 *
 * Color alone cannot carry the state — it is invisible to a colorblind user and to TalkBack — so
 * every status shown in the UI pairs this icon with a spoken description.
 */
val ReadingStatus.icon: ImageVector
    get() = when (this) {
        ReadingStatus.Ok -> Icons.Default.CheckCircle
        ReadingStatus.Warning -> Icons.Default.Warning
        ReadingStatus.Problem -> Icons.Default.Error
        ReadingStatus.Neutral -> Icons.Default.Info
    }
