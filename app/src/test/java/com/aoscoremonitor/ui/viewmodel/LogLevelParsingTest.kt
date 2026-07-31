package com.aoscoremonitor.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers [parseLogLevel], which decides how every log line is colored.
 *
 * The behaviour it replaced scanned each line for `" E "`, `"error"`, `" W "` and so on while
 * drawing. [levelComesFromTheFormatsLevelField] is the case that motivated the change: a line
 * whose message happens to contain a standalone capital E is not an error line.
 */
class LogLevelParsingTest {

    @Test
    fun levelComesFromTheFormatsLevelField() {
        val infoLine = "07-31 11:11:07.104  1731  1731 I ActivityManager: Received signal E from PID 4821"

        assertEquals(LogLevel.Info, parseLogLevel(infoLine))
    }

    @Test
    fun everyStandardLevelIsRecognised() {
        assertEquals(LogLevel.Verbose, parseLogLevel(threadTime("V", "trace")))
        assertEquals(LogLevel.Debug, parseLogLevel(threadTime("D", "detail")))
        assertEquals(LogLevel.Info, parseLogLevel(threadTime("I", "started")))
        assertEquals(LogLevel.Warn, parseLogLevel(threadTime("W", "slow")))
        assertEquals(LogLevel.Error, parseLogLevel(threadTime("E", "failed")))
    }

    @Test
    fun fatalCountsAsAnError() {
        assertEquals(LogLevel.Error, parseLogLevel(threadTime("F", "abort")))
    }

    @Test
    fun linesInAnotherFormatFallBackToKeywords() {
        assertEquals(LogLevel.Error, parseLogLevel("java.lang.IllegalStateException: no such window"))
        assertEquals(LogLevel.Warn, parseLogLevel("Warning: display refresh rate mismatch"))
        assertEquals(LogLevel.Unknown, parseLogLevel("--------- beginning of main"))
    }

    private fun threadTime(level: String, message: String) = "07-31 11:11:07.104  1731  1731 $level SomeTag: $message"
}
