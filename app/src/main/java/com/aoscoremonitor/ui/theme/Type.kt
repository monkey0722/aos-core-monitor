package com.aoscoremonitor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/**
 * Material's default type scale, plus a monospace style for machine output.
 *
 * The previous file redefined `bodyLarge` with exactly Material's own values and left the rest
 * commented out, so it only added noise. Log lines, /proc values, and dumpsys output are
 * column-aligned at the source, though, and a proportional face throws that alignment away —
 * [MonitorTypography.machineText] keeps it.
 */
val Typography = Typography()

object MonitorTypography {
    /** For text that came out of a device tool verbatim — logcat lines, dumpsys, /proc readings. */
    val machineText: TextStyle
        get() = Typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
}
