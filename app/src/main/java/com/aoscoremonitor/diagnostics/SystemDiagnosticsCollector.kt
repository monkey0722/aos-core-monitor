package com.aoscoremonitor.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A class that analyzes system state using public APIs of AOSP internal services.
 */
class SystemDiagnosticsCollector(
    private val context: Context,
    private val onDiagnosticsUpdated: (DiagnosticsInfo) -> Unit
) {
    /**
     * Data class containing system diagnostic information.
     */
    data class DiagnosticsInfo(
        val runningProcesses: List<String>,
        val availableMemory: String,
        val screenOn: Boolean,
        val dumpsysResult: String
    )

    // Maintain CoroutineScope at class level
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    /**
     * Starts collecting system diagnostic information periodically.
     */
    fun startCollecting() {
        // Don't create a new job if collection is already in progress
        if (job?.isActive == true) return

        job = scope.launch {
            try {
                val activityManager =
                    context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

                while (isActive) {
                    // --- Get running process information from ActivityManager ---
                    val runningAppProcesses = activityManager.runningAppProcesses
                    val processNames = runningAppProcesses?.map { it.processName } ?: emptyList()

                    // --- Get memory information from ActivityManager ---
                    val memoryInfo = ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(memoryInfo)
                    val availMemMB = memoryInfo.availMem / (1024 * 1024)
                    val memoryStatus = "Available: $availMemMB MB"

                    // --- Get screen state from PowerManager ---
                    val screenOn = powerManager.isInteractive

                    // --- Get dumpsys command result ---
                    val dumpsysResult = readDumpsysResult()

                    // --- Notify diagnostic information ---
                    val diagnosticsInfo = DiagnosticsInfo(
                        runningProcesses = processNames,
                        availableMemory = memoryStatus,
                        screenOn = screenOn,
                        dumpsysResult = dumpsysResult
                    )

                    withContext(Dispatchers.Main) {
                        onDiagnosticsUpdated(diagnosticsInfo)
                    }

                    delay(2000L) // Update every 2 seconds
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    /**
     * Stops collecting diagnostic information.
     */
    fun stopCollecting() {
        job?.cancel()
        job = null
    }

    /**
     * Gets the result of dumpsys meminfo.
     */
    private fun readDumpsysResult(): String {
        return try {
            val process = Runtime.getRuntime().exec("dumpsys meminfo")
            process.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Error reading dumpsys: ${e.message}"
        }
    }
}
