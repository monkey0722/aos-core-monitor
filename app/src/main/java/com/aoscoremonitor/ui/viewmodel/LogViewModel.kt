package com.aoscoremonitor.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.aoscoremonitor.diagnostics.LogCollector

/** Severity of a logcat line, read once when the line arrives. */
enum class LogLevel {
    Verbose,
    Debug,
    Info,
    Warn,
    Error,
    Unknown
}

/**
 * One line of logcat output.
 *
 * [id] exists so the list can be keyed. Lines are not unique — the same message repeats — so
 * keying on the text would collide and make Compose reuse the wrong row.
 */
data class LogLine(val id: Long, val text: String, val level: LogLevel)

/**
 * Collects logcat for the log screen and keeps the most recent [MAX_LINES] of it.
 *
 * Two things were wrong with collecting into the activity instead. The list had no upper bound,
 * and logcat produces lines faster than anyone reads them, so leaving the app running was enough
 * to exhaust memory. And the collector was tied to the activity rather than the screen, so it
 * kept reading whether or not the log screen was open.
 *
 * The lines are held in a snapshot list rather than a `StateFlow`. A flow would have to publish a
 * fresh list per line, which at logcat's rate means allocating thousands of copies of a
 * two-thousand-element list per second; appending to a snapshot list is O(1) and Compose still
 * coalesces the resulting recompositions to one per frame.
 */
class LogViewModel : ViewModel() {

    private val _lines = mutableStateListOf<LogLine>()

    /** Most recent lines, oldest first. Capped at [MAX_LINES]. */
    val lines: List<LogLine> = _lines

    /** How many lines have been evicted to stay within the cap, so the UI can say so. */
    var droppedCount by mutableIntStateOf(0)
        private set

    private var nextId = 0L

    private val collector = LogCollector { line -> append(line) }

    /**
     * Starts reading logcat.
     *
     * Safe to call repeatedly — [LogCollector] ignores a start while it is already collecting.
     */
    fun startCollecting() = collector.startCollecting()

    fun stopCollecting() = collector.stopCollecting()

    override fun onCleared() {
        collector.stopCollecting()
    }

    private fun append(text: String) {
        _lines.add(LogLine(id = nextId++, text = text, level = parseLogLevel(text)))
        if (_lines.size > MAX_LINES) {
            val excess = _lines.size - MAX_LINES
            _lines.removeRange(0, excess)
            droppedCount += excess
        }
    }

    private companion object {
        /**
         * Roughly a screenful of scrollback at any plausible reading speed, and small enough that
         * the retained strings stay well under a megabyte.
         */
        const val MAX_LINES = 2_000
    }
}

/**
 * The level field of logcat's default `threadtime` format:
 * `MM-DD HH:MM:SS.mmm  PID  TID L TAG: message`.
 */
private val ThreadTimeLevel = Regex("""^\d{2}-\d{2} [\d:.]+\s+\d+\s+\d+\s+([VDIWEFS])\s""")

/**
 * Reads the severity of a logcat line.
 *
 * The screen used to do this while drawing, running up to eight case-insensitive substring scans
 * over every visible line on every recomposition — and `" E "` matched any message containing a
 * lone capital E, not just an error. Anchoring to the format's level field is both cheaper and
 * more accurate; the keyword scan stays as a fallback for lines in other formats.
 */
internal fun parseLogLevel(line: String): LogLevel = when (ThreadTimeLevel.find(line)?.groupValues?.get(1)) {
    "V" -> LogLevel.Verbose
    "D" -> LogLevel.Debug
    "I" -> LogLevel.Info
    "W" -> LogLevel.Warn
    "E", "F" -> LogLevel.Error
    else -> when {
        line.contains("error", ignoreCase = true) ||
            line.contains("exception", ignoreCase = true) -> LogLevel.Error
        line.contains("warning", ignoreCase = true) -> LogLevel.Warn
        line.contains("info", ignoreCase = true) -> LogLevel.Info
        line.contains("debug", ignoreCase = true) -> LogLevel.Debug
        else -> LogLevel.Unknown
    }
}
