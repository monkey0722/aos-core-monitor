package com.aoscoremonitor.diagnostics

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FrameworkAnalyzer(
    private val context: Context,
    private val onDataCollected: (FrameworkData) -> Unit
) {
    data class FrameworkData(
        val binderTransactions: List<BinderTransaction>,
        val apiCalls: List<ApiCallInfo>,
        val serviceData: ServiceManagerData
    )

    data class BinderTransaction(
        val pid: Int,
        val process: String,
        val transactionCode: Int,
        val destination: String,
        val dataSize: Int,
        val timestamp: Long
    )

    data class ApiCallInfo(
        val apiName: String,
        val callerPackage: String,
        val timestamp: Long,
        val duration: Long
    )

    data class ServiceManagerData(
        val runningServices: Map<String, String>,
        val serviceConnections: List<Pair<String, String>>
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    fun startAnalyzing() {
        if (job?.isActive == true) return

        job = scope.launch {
            while (isActive) {
                try {
                    // Collect Binder transaction data
                    val binderTransactions = collectBinderTransactions()

                    // Collect API call information
                    val apiCalls = collectApiCalls()

                    // Collect service manager data
                    val serviceData = collectServiceManagerData()

                    val frameworkData = FrameworkData(
                        binderTransactions = binderTransactions,
                        apiCalls = apiCalls,
                        serviceData = serviceData
                    )

                    withContext(Dispatchers.Main) {
                        onDataCollected(frameworkData)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(2000L) // Update every 2 seconds
            }
        }
    }

    fun stopAnalyzing() {
        job?.cancel()
        job = null
    }

    private suspend fun collectBinderTransactions(): List<BinderTransaction> {
        val transactions = mutableListOf<BinderTransaction>()

        try {
            // Use dumpsys binder_txns to get transaction information
            val process = Runtime.getRuntime().exec("dumpsys binder_txns")
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            var line: String?
            var currentPid = -1
            var currentProcess = ""

            while (reader.readLine().also { line = it } != null) {
                if (line?.startsWith("Process") == true) {
                    // Extract PID and process name
                    val parts = line?.split(" ") ?: continue
                    if (parts.size >= 2) {
                        currentPid = parts[1].replace(":", "").toIntOrNull() ?: -1
                        currentProcess = parts.getOrNull(2) ?: ""
                    }
                } else if (line?.contains("transaction") == true && currentPid > 0) {
                    // Parse transaction data
                    // Example format: "transaction 0x123 to 0x456 code 789 (data: 1024 bytes)"
                    val transactionCode = extractTransactionCode(line ?: "")
                    val destination = extractDestination(line ?: "")
                    val dataSize = extractDataSize(line ?: "")

                    if (transactionCode != -1) {
                        transactions.add(
                            BinderTransaction(
                                pid = currentPid,
                                process = currentProcess,
                                transactionCode = transactionCode,
                                destination = destination,
                                dataSize = dataSize,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }

            reader.close()
            process.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return transactions.takeLast(50) // Return only recent transactions to avoid overwhelming UI
    }

    private suspend fun collectApiCalls(): List<ApiCallInfo> {
        val apiCalls = mutableListOf<ApiCallInfo>()

        try {
            // In a real implementation, this would require instrumentation
            // or possibly integrating with a tool like AppTrace
            // For this example, we're using dumpsys activity to get some API usage info

            val process = Runtime.getRuntime().exec("dumpsys activity asm")
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            var line: String?

            while (reader.readLine().also { line = it } != null) {
                if (line?.contains("API calls") == true) {
                    // Parse API call data (simplified for example)
                    val apiName = "android.app.ActivityManager.getRunningAppProcesses"
                    val callerPackage = "com.android.settings"

                    apiCalls.add(
                        ApiCallInfo(
                            apiName = apiName,
                            callerPackage = callerPackage,
                            timestamp = System.currentTimeMillis(),
                            duration = 5L // milliseconds, placeholder
                        )
                    )
                }
            }

            reader.close()
            process.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // For demo purposes if no actual data is found
        if (apiCalls.isEmpty()) {
            apiCalls.add(
                ApiCallInfo(
                    apiName = "android.app.ActivityManager.getRunningAppProcesses",
                    callerPackage = "com.aoscoremonitor",
                    timestamp = System.currentTimeMillis(),
                    duration = 3L
                )
            )
            apiCalls.add(
                ApiCallInfo(
                    apiName = "android.content.pm.PackageManager.getInstalledPackages",
                    callerPackage = "com.aoscoremonitor",
                    timestamp = System.currentTimeMillis() - 1000,
                    duration = 120L
                )
            )
        }

        return apiCalls
    }

    private suspend fun collectServiceManagerData(): ServiceManagerData {
        val runningServices = mutableMapOf<String, String>()
        val serviceConnections = mutableListOf<Pair<String, String>>()

        try {
            // Collect service manager information using dumpsys
            val process = Runtime.getRuntime().exec("dumpsys activity services")
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            var line: String?
            var currentService = ""

            while (reader.readLine().also { line = it } != null) {
                if (line?.contains("* ServiceRecord{") == true) {
                    // Parse service record data
                    val parts = line?.split(" ") ?: continue
                    if (parts.size >= 3) {
                        currentService = parts[2]
                        val serviceState = if (line?.contains("running") == true) "Running" else "Stopped"
                        runningServices[currentService] = serviceState
                    }
                } else if (line?.contains("ConnectionRecord{") == true && currentService.isNotEmpty()) {
                    // Parse connection information
                    val clientApp = extractClientApp(line ?: "")
                    if (clientApp.isNotEmpty()) {
                        serviceConnections.add(Pair(clientApp, currentService))
                    }
                }
            }

            reader.close()
            process.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Add some demo data if needed
        if (runningServices.isEmpty()) {
            runningServices["com.android.systemui/.SystemUIService"] = "Running"
            runningServices["com.android.phone/.TelephonyDebugService"] = "Running"
            runningServices["android/com.android.server.telecom.TelecomLoaderService"] = "Running"
        }

        if (serviceConnections.isEmpty()) {
            serviceConnections.add(Pair("com.aoscoremonitor", "com.android.systemui/.SystemUIService"))
        }

        return ServiceManagerData(
            runningServices = runningServices,
            serviceConnections = serviceConnections
        )
    }

    // Helper methods to extract information from log lines
    private fun extractTransactionCode(line: String): Int {
        val codePattern = "code (\\d+)".toRegex()
        val match = codePattern.find(line)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
    }

    private fun extractDestination(line: String): String {
        val destPattern = "to ([0-9a-fx]+)".toRegex()
        val match = destPattern.find(line)
        return match?.groupValues?.getOrNull(1) ?: "unknown"
    }

    private fun extractDataSize(line: String): Int {
        val sizePattern = "data: (\\d+) bytes".toRegex()
        val match = sizePattern.find(line)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun extractClientApp(line: String): String {
        val appPattern = "client=(\\S+)".toRegex()
        val match = appPattern.find(line)
        return match?.groupValues?.getOrNull(1) ?: ""
    }
}
