package com.aoscoremonitor.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A class that periodically collects various system information (CPU/Memory, Battery, Network)
 * and notifies through callbacks.
 */
class SystemInfoCollector(
    private val context: Context,
    private val onInfoUpdated: (SystemInfo) -> Unit
) {
    data class SystemInfo(
        val cpuUsage: String,
        val memoryUsage: String,
        val batteryStatus: String,
        val networkStatus: String
    )

    // Define dedicated CoroutineScope with SupervisorJob
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    // Data class and variable for holding CPU statistics
    private data class CpuStats(val total: Long, val idle: Long)
    private var lastCpuStats: CpuStats? = null

    /**
     * Starts collecting system information.
     */
    fun startCollecting() {
        if (job?.isActive == true) return

        job = scope.launch {
            while (isActive) {
                try {
                    val cpuUsage = readCpuUsage()
                    val memoryUsage = readMemoryUsage()
                    val batteryStatus = readBatteryStatus()
                    val networkStatus = readNetworkStatus()
                    // Execute callback on the main thread
                    withContext(Dispatchers.Main) {
                        onInfoUpdated(
                            SystemInfo(
                                cpuUsage = cpuUsage,
                                memoryUsage = memoryUsage,
                                batteryStatus = batteryStatus,
                                networkStatus = networkStatus
                            )
                        )
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
                // Update every second
                delay(1000L)
            }
        }
    }

    /**
     * Stops collecting system information.
     */
    fun stopCollecting() {
        job?.cancel()
        job = null
    }

    /**
     * Calculates CPU usage.
     * Computes the difference from the previous reading of /proc/stat to calculate CPU usage.
     * Returns "CPU: N/A" for the first reading.
     */
    private fun readCpuUsage(): String {
        return try {
            RandomAccessFile("/proc/stat", "r").use { reader ->
                val load = reader.readLine() ?: return "CPU: N/A"
                val toks = load.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                if (toks.size < 5) return "CPU: N/A"
                // toks[0] is "cpu", followed by user, nice, system, idle values
                val user = toks[1].toLong()
                val nice = toks[2].toLong()
                val system = toks[3].toLong()
                val idle = toks[4].toLong()
                val total = user + nice + system + idle
                val currentStats = CpuStats(total, idle)
                val usage = if (lastCpuStats != null) {
                    val diffTotal = currentStats.total - lastCpuStats!!.total
                    val diffIdle = currentStats.idle - lastCpuStats!!.idle
                    if (diffTotal > 0) ((diffTotal - diffIdle) * 100.0 / diffTotal).toInt() else 0
                } else {
                    null
                }
                lastCpuStats = currentStats
                if (usage != null) "CPU: $usage%" else "CPU: N/A"
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            "CPU: N/A"
        }
    }

    /**
     * Retrieves memory usage information.
     */
    private fun readMemoryUsage(): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val availMemMB = memoryInfo.availMem / (1024 * 1024)
        return "Available Memory: $availMemMB MB"
    }

    /**
     * Retrieves battery status information.
     */
    private fun readBatteryStatus(): String {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return "Battery: $batteryLevel%"
    }

    /**
     * Retrieves network status information.
     * Uses the latest ConnectivityManager API to check connection status.
     */
    private fun readNetworkStatus(): String {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return "Not connected"
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return "Not connected"
        return when {
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                "Connected: WIFI"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                "Connected: Cellular"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                "Connected: Ethernet"
            else -> "Connected: Other"
        }
    }
}
