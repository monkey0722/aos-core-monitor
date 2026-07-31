package com.aoscoremonitor.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.SystemClock
import java.io.File
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
class SystemInfoCollector(private val context: Context, private val onUpdate: (SystemInfo) -> Unit) {
    companion object {
        // Android sets USER_HZ to 100, so one clock tick in /proc/self/stat is 10ms
        private const val MILLIS_PER_TICK = 10L
    }

    data class SystemInfo(val cpuUsage: String, val memoryUsage: String, val batteryStatus: String, val networkStatus: String)

    // Define dedicated CoroutineScope with SupervisorJob
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    // Data classes and variables for holding CPU statistics
    private data class CpuStats(val total: Long, val idle: Long)
    private data class OwnCpuStats(val ticks: Long, val uptimeMs: Long)
    private var lastCpuStats: CpuStats? = null
    private var lastOwnCpuStats: OwnCpuStats? = null

    /**
     * Starts collecting system information.
     */
    fun start() {
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
                        onUpdate(
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
    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * Reports CPU usage.
     *
     * System-wide usage comes from /proc/stat, but SELinux denies that file to the app sandbox on
     * modern Android, so this falls back to the app's own usage from /proc/self/stat, which stays
     * readable. The returned string says which of the two it is, so the UI never shows a bare
     * "N/A" that could mean either "still measuring" or "blocked by the platform".
     */
    private fun readCpuUsage(): String {
        readSystemCpuUsage()?.let { return "$it%" }
        readOwnCpuUsage()?.let { return "$it% (this app)" }
        return if (lastCpuStats == null && lastOwnCpuStats == null) {
            "Restricted — /proc/stat is not readable by apps"
        } else {
            "Measuring…"
        }
    }

    /**
     * Reads system-wide CPU usage from /proc/stat.
     * @return usage percentage, or null when the file is unreadable or no delta is available yet
     */
    private fun readSystemCpuUsage(): Int? {
        return try {
            RandomAccessFile("/proc/stat", "r").use { reader ->
                val load = reader.readLine() ?: return null
                val toks = load.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                if (toks.size < 5) return null
                // toks[0] is "cpu", followed by user, nice, system, idle values
                val user = toks[1].toLong()
                val nice = toks[2].toLong()
                val system = toks[3].toLong()
                val idle = toks[4].toLong()
                val current = CpuStats(user + nice + system + idle, idle)
                val previous = lastCpuStats
                lastCpuStats = current

                if (previous == null) return null
                val diffTotal = current.total - previous.total
                val diffIdle = current.idle - previous.idle
                if (diffTotal > 0) ((diffTotal - diffIdle) * 100.0 / diffTotal).toInt() else 0
            }
        } catch (ex: Exception) {
            // Expected on modern Android: /proc/stat is blocked for untrusted apps
            null
        }
    }

    /**
     * Reads this process's CPU usage from /proc/self/stat, which the app sandbox can always read.
     * Fields 14 and 15 are utime and stime in clock ticks.
     * @return usage percentage across all cores, or null when no delta is available yet
     */
    private fun readOwnCpuUsage(): Int? {
        return try {
            // Field 2 (comm) is parenthesised and may contain spaces, so start parsing after it.
            // The first field that follows is state, so utime and stime land at index 11 and 12.
            val afterComm = File("/proc/self/stat").readText().substringAfterLast(") ")
            val fields = afterComm.split(" ")
            if (fields.size < 13) return null
            val ticks = fields[11].toLong() + fields[12].toLong()
            val uptimeMs = SystemClock.elapsedRealtime()
            val current = OwnCpuStats(ticks, uptimeMs)
            val previous = lastOwnCpuStats
            lastOwnCpuStats = current

            if (previous == null) return null
            val elapsedMs = current.uptimeMs - previous.uptimeMs
            if (elapsedMs <= 0) return null
            val cpuMs = (current.ticks - previous.ticks) * MILLIS_PER_TICK
            (cpuMs * 100.0 / elapsedMs).toInt().coerceIn(0, 100 * Runtime.getRuntime().availableProcessors())
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
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
        return "$availMemMB MB available"
    }

    /**
     * Retrieves battery status information.
     */
    private fun readBatteryStatus(): String {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return "$batteryLevel%"
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
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
    }
}
