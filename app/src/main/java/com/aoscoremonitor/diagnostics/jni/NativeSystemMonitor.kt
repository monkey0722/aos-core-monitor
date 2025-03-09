package com.aoscoremonitor.diagnostics.jni

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class NativeSystemMonitor {
    companion object {
        private const val TAG = "NativeSystemMonitor"

        // Loading the native library
        init {
            try {
                System.loadLibrary("system_monitor")
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
            }
        }
    }

    // Native methods
    external fun getCpuInfoNative(): String
    external fun getMemInfoNative(): String
    external fun getProcessInfoNative(pid: Int): String
    external fun getNetworkStatsNative(): String

    suspend fun getCpuInfo(): Map<String, Long> = withContext(Dispatchers.IO) {
        val cpuInfo = mutableMapOf<String, Long>()
        try {
            val rawData = getCpuInfoNative()
            val parts = rawData.split("\\s+".toRegex())
            if (parts.size >= 8) {
                cpuInfo["user"] = parts[1].toLongOrNull() ?: 0
                cpuInfo["nice"] = parts[2].toLongOrNull() ?: 0
                cpuInfo["system"] = parts[3].toLongOrNull() ?: 0
                cpuInfo["idle"] = parts[4].toLongOrNull() ?: 0
                cpuInfo["iowait"] = parts[5].toLongOrNull() ?: 0
                cpuInfo["irq"] = parts[6].toLongOrNull() ?: 0
                cpuInfo["softirq"] = parts[7].toLongOrNull() ?: 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing CPU info", e)
        }
        cpuInfo
    }

    suspend fun getMemInfo(): Map<String, Long> = withContext(Dispatchers.IO) {
        val memInfo = mutableMapOf<String, Long>()
        try {
            val rawData = getMemInfoNative()
            for (line in rawData.split("\n")) {
                if (line.isBlank()) continue
                val colonIndex = line.indexOf(':')
                if (colonIndex > 0) {
                    val key = line.substring(0, colonIndex).trim()
                    val valueParts = line.substring(colonIndex + 1).trim().split("\\s+".toRegex())
                    if (valueParts.isNotEmpty()) {
                        val value = valueParts[0].toLongOrNull() ?: 0
                        memInfo[key] = value
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing memory info", e)
        }
        memInfo
    }

    suspend fun getProcessInfo(pid: Int): Map<String, String> = withContext(Dispatchers.IO) {
        val processInfo = mutableMapOf<String, String>()
        try {
            val rawData = getProcessInfoNative(pid)
            for (line in rawData.split("\n")) {
                if (line.isBlank()) continue
                val colonIndex = line.indexOf(':')
                if (colonIndex > 0) {
                    val key = line.substring(0, colonIndex).trim()
                    val value = line.substring(colonIndex + 1).trim()
                    processInfo[key] = value
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing process info", e)
        }
        processInfo
    }

    data class InterfaceStats(
        val rxBytes: Long,
        val rxPackets: Long,
        val rxErrors: Long,
        val rxDropped: Long,
        val txBytes: Long,
        val txPackets: Long,
        val txErrors: Long,
        val txDropped: Long
    ) {
        fun getFormattedRxBytes(): String = formatBytes(rxBytes)
        fun getFormattedTxBytes(): String = formatBytes(txBytes)

        private fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                else -> "${bytes / (1024 * 1024 * 1024)} GB"
            }
        }
    }

    suspend fun getNetworkStats(): Map<String, InterfaceStats> = withContext(Dispatchers.IO) {
        val networkStats = mutableMapOf<String, InterfaceStats>()
        try {
            val jsonData = getNetworkStatsNative()
            val jsonObject = JSONObject(jsonData)

            val interfaceNames = jsonObject.keys()
            while (interfaceNames.hasNext()) {
                val interfaceName = interfaceNames.next()
                val interfaceData = jsonObject.getJSONObject(interfaceName)

                networkStats[interfaceName] = InterfaceStats(
                    rxBytes = interfaceData.getLong("rx_bytes"),
                    rxPackets = interfaceData.getLong("rx_packets"),
                    rxErrors = interfaceData.getLong("rx_errors"),
                    rxDropped = interfaceData.getLong("rx_dropped"),
                    txBytes = interfaceData.getLong("tx_bytes"),
                    txPackets = interfaceData.getLong("tx_packets"),
                    txErrors = interfaceData.getLong("tx_errors"),
                    txDropped = interfaceData.getLong("tx_dropped")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing network stats", e)
        }

        // Use dummy data if data could not be obtained.
        if (networkStats.isEmpty()) {
            return@withContext getDummyNetworkStats()
        }

        networkStats
    }

    private fun getDummyNetworkStats(): Map<String, InterfaceStats> {
        return mapOf(
            "dummy:wlan0" to InterfaceStats(
                rxBytes = 1024 * 1024 * 50, // 50 MB
                rxPackets = 1500,
                rxErrors = 2,
                rxDropped = 0,
                txBytes = 1024 * 1024 * 10, // 10 MB
                txPackets = 800,
                txErrors = 0,
                txDropped = 1
            ),
            "dummy:eth0" to InterfaceStats(
                rxBytes = 1024 * 1024 * 25, // 25 MB
                rxPackets = 1200,
                rxErrors = 1,
                rxDropped = 0,
                txBytes = 1024 * 1024 * 5, // 5 MB
                txPackets = 600,
                txErrors = 0,
                txDropped = 0
            ),
            "dummy:rmnet0" to InterfaceStats(
                rxBytes = 1024 * 1024 * 120, // 120 MB
                rxPackets = 3500,
                rxErrors = 5,
                rxDropped = 2,
                txBytes = 1024 * 1024 * 30, // 30 MB
                txPackets = 2200,
                txErrors = 1,
                txDropped = 3
            )
        )
    }
}
