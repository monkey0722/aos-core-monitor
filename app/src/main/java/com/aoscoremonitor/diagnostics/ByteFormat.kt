package com.aoscoremonitor.diagnostics

import java.util.Locale

/**
 * Renders a byte count at a readable scale.
 *
 * Every screen that shows a size was about to grow its own copy of this — the network screen
 * already had one — so it lives in one place. Gigabytes and above carry one decimal: a filesystem
 * shown as "107 GB" whether it holds 107.0 or 107.9 hides more than the digit costs, while a
 * decimal on kilobytes would be noise.
 *
 * Binary multiples throughout, matching what /proc and statvfs report.
 */
fun formatBytes(bytes: Long): String = when {
    bytes < KILO -> "$bytes B"
    bytes < MEGA -> "${bytes / KILO} KB"
    bytes < GIGA -> "${bytes / MEGA} MB"
    bytes < TERA -> String.format(Locale.US, "%.1f GB", bytes.toDouble() / GIGA)
    else -> String.format(Locale.US, "%.1f TB", bytes.toDouble() / TERA)
}

/** The same, for the kilobyte counters /proc reports. */
fun formatKilobytes(kilobytes: Long): String = formatBytes(kilobytes * KILO)

private const val KILO = 1024L
private const val MEGA = KILO * 1024
private const val GIGA = MEGA * 1024
private const val TERA = GIGA * 1024
