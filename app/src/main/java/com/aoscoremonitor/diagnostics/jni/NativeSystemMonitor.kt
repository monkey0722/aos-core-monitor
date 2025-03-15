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
    external fun getTcpConnectionsNative(): String

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

    // Data class for TCP connection information
    data class TcpConnection(
        val localAddress: String,
        val remoteAddress: String,
        val status: String,
        val uid: Int,
        val inode: String
    ) {
        fun getFormattedLocalAddress(): String = formatAddress(localAddress)
        fun getFormattedRemoteAddress(): String = formatAddress(remoteAddress)

        private fun formatAddress(hexAddress: String): String {
            // Example: "0100007F:0050" → "127.0.0.1:80"
            val parts = hexAddress.split(":")
            if (parts.size != 2) return hexAddress

            val ipHex = parts[0]
            val port = Integer.parseInt(parts[1], 16)

            val ip = StringBuilder()
            for (i in (ipHex.length - 2) downTo 0 step 2) {
                val octet = ipHex.substring(i, i + 2)
                ip.append(Integer.parseInt(octet, 16))
                if (i > 0) ip.append(".")
            }

            return "$ip:$port"
        }
    }

    // Function to retrieve TCP connection information
    suspend fun getTcpConnections(): List<TcpConnection> = withContext(Dispatchers.IO) {
        val connections = mutableListOf<TcpConnection>()
        try {
            val jsonData = getTcpConnectionsNative()
            val jsonObject = JSONObject(jsonData)
            val connectionsArray = jsonObject.getJSONArray("connections")

            for (i in 0 until connectionsArray.length()) {
                val conn = connectionsArray.getJSONObject(i)
                connections.add(
                    TcpConnection(
                        localAddress = conn.getString("local_address"),
                        remoteAddress = conn.getString("remote_address"),
                        status = conn.getString("status"),
                        uid = conn.getInt("uid"),
                        inode = conn.getString("inode")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing TCP connections", e)
        }

        if (connections.isEmpty()) {
            Log.i(TAG, "Using dummy TCP connection data")
            return@withContext getDummyTcpConnections()
        }

        connections
    }

    private fun getDummyTcpConnections(): List<TcpConnection> {
        return listOf(
            TcpConnection(
                localAddress = "0100007F:1F90", // 127.0.0.1:8080
                remoteAddress = "00000000:0000", // 0.0.0.0:0
                status = "LISTEN",
                uid = 10123,
                inode = "12345"
            ),
            TcpConnection(
                localAddress = "0100007F:01BB", // 127.0.0.1:443
                remoteAddress = "630A000A:C642", // 10.0.10.99:50754
                status = "ESTABLISHED",
                uid = 10045,
                inode = "23456"
            ),
            TcpConnection(
                localAddress = "0100007F:0050", // 127.0.0.1:80
                remoteAddress = "540B000A:A2B6", // 10.0.11.84:41654
                status = "TIME_WAIT",
                uid = 10045,
                inode = "34567"
            ),
            TcpConnection(
                localAddress = "0100007F:0050", // 127.0.0.1:80
                remoteAddress = "2C0A000A:F1A2", // 10.0.10.44:61858
                status = "ESTABLISHED",
                uid = 10045,
                inode = "45678"
            ),
            TcpConnection(
                localAddress = "78563412:0CEA", // 18.52.86.120:3338
                remoteAddress = "9A3C7856:01BB", // 86.120.60.154:443
                status = "ESTABLISHED",
                uid = 10073,
                inode = "56789"
            )
        )
    }
}
