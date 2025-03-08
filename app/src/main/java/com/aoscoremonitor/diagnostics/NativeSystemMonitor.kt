package com.aoscoremonitor.diagnostics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NativeSystemMonitor {
    companion object {
        private const val TAG = "NativeSystemMonitor"

        // 　Roading the native library
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
}
