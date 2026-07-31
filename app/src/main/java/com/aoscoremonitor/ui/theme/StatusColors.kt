package com.aoscoremonitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colors for the three states a reading can be in, sitting alongside the Material color scheme.
 *
 * Screens previously reached for [MaterialTheme.colorScheme] roles to mean "healthy" or
 * "degraded" — tertiary for a warning here, inversePrimary for an icon there — which made the
 * same state look different on every screen and left some icons below the contrast floor. A
 * reading's state now maps to exactly one of these.
 */
@Immutable
data class StatusColors(
    val ok: Color,
    val okContainer: Color,
    val onOkContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color
)

internal val LightStatusColors = StatusColors(
    ok = OkLight,
    okContainer = OkContainerLight,
    onOkContainer = OnOkContainerLight,
    warning = WarningLight,
    warningContainer = WarningContainerLight,
    onWarningContainer = OnWarningContainerLight
)

internal val DarkStatusColors = StatusColors(
    ok = OkDark,
    okContainer = OkContainerDark,
    onOkContainer = OnOkContainerDark,
    warning = WarningDark,
    warningContainer = WarningContainerDark,
    onWarningContainer = OnWarningContainerDark
)

internal val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

/** Status colors for the current theme. Reads like [MaterialTheme.colorScheme] at call sites. */
val MaterialTheme.statusColors: StatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalStatusColors.current
