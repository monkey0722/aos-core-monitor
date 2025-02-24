package com.aoscoremonitor.diagnostics

import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A class that asynchronously collects system logs and notifies log lines through a callback.
 *
 * This class uses the Android logcat command to collect system logs in real-time.
 * Note: Running logcat requires appropriate device permissions, so careful consideration
 * is needed when deploying to production.
 */
class LogCollector(private val onLogLine: (String) -> Unit) {

    // Dedicated CoroutineScope with SupervisorJob for error isolation
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var logJob: Job? = null
    private var process: Process? = null

    /**
     * Starts collecting system logs.
     * * This method launches a coroutine that continuously reads from the logcat process
     * and forwards each log line to the callback provided in the constructor.
     */
    fun startCollecting() {
        // Skip if already collecting logs
        if (logJob?.isActive == true) return

        logJob = scope.launch {
            try {
                process = Runtime.getRuntime().exec("logcat")
                process?.let { proc ->
                    BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            // Switch to main thread for UI updates via callback
                            withContext(Dispatchers.Main) {
                                onLogLine(line)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Clean up resources
                process?.destroy()
                process = null
            }
        }
    }

    /**
     * Stops collecting system logs.
     * * This method cancels the log collection coroutine and cleans up associated resources,
     * including the logcat process.
     */
    fun stopCollecting() {
        logJob?.cancel()
        logJob = null
        process?.destroy()
        process = null
    }
}
