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

/**
 * Periodically reads the device's HAL interfaces, hardware services and VNDK libraries, and hands
 * each pass to [onUpdate].
 */
class HalInterfaceCollector(private val context: Context, private val onUpdate: (HalData) -> Unit) {
    data class HalData(val halInterfaces: Collected<List<HalInterface>>, val hwServices: Collected<List<HwService>>, val vndkInfo: VndkInfo)

    data class HalInterface(
        val name: String,
        val version: String,
        // HIDL, AIDL, etc.
        val type: String,
        val implementation: String,
        val status: String
    )

    /**
     * One entry from `service list`: a name registered with servicemanager, and the interface
     * descriptor it publishes.
     *
     * This used to carry `server` and `clients` as well. Neither came from the device: every entry
     * was given `server = "system_server"` and the same two client packages, because `service list`
     * reports neither. The screen presented them as readings. The descriptor below is what that
     * command actually prints.
     */
    data class HwService(val name: String, val interfaceDescriptor: String)

    /**
     * The VNDK version this build declares, if any.
     *
     * The library list that used to hang off this was a hard-coded set of five names — the same
     * five whether or not the property was readable — with a fabricated "30" standing in for the
     * version when `getprop` returned nothing. Both are gone: the libraries actually mapped into
     * the process are the loaded-libraries screen's subject, and are read from the linker there.
     */
    data class VndkInfo(val version: String?)

    companion object {
        /**
         * Shown when lshal produces nothing — it needs privileges this app does not have, and on
         * some builds it crashes outright. Presented to the UI as sample data, never as a reading.
         */
        private val SAMPLE_HAL_INTERFACES = listOf(
            HalInterface(
                name = "android.hardware.audio@7.0::IDevicesFactory",
                version = "7.0",
                type = "HIDL",
                implementation = "default",
                status = "Running"
            ),
            HalInterface(
                name = "android.hardware.camera@2.5::ICameraProvider",
                version = "2.5",
                type = "HIDL",
                implementation = "qcom",
                status = "Running"
            ),
            HalInterface(
                name = "android.hardware.bluetooth@1.1::IBluetoothHci",
                version = "1.1",
                type = "HIDL",
                implementation = "default",
                status = "Running"
            ),
            HalInterface(
                name = "android.hardware.sensors@2.1::ISensors",
                version = "2.1",
                type = "HIDL",
                implementation = "default",
                status = "Running"
            ),
            HalInterface(
                name = "android.hardware.nfc@1.2::INfc",
                version = "1.2",
                type = "HIDL",
                implementation = "default",
                status = "Running"
            )
        )

        /** Shown when `service list` produces nothing. */
        private val SAMPLE_HW_SERVICES = listOf(
            HwService(name = "SurfaceFlinger", interfaceDescriptor = "android.ui.ISurfaceComposer"),
            HwService(name = "audio", interfaceDescriptor = "android.media.IAudioService"),
            HwService(name = "power", interfaceDescriptor = "android.os.IPowerManager")
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return

        job = scope.launch {
            while (isActive) {
                try {
                    // Collect HAL interface data
                    val halInterfaces = collectHalInterfaces()

                    // Collect hardware service data
                    val hwServices = collectHwServices()

                    // Collect VNDK information
                    val vndkInfo = collectVndkInfo()

                    val halData = HalData(
                        halInterfaces = Collected.realOrSample(halInterfaces, SAMPLE_HAL_INTERFACES),
                        hwServices = Collected.realOrSample(hwServices, SAMPLE_HW_SERVICES),
                        vndkInfo = vndkInfo
                    )

                    withContext(Dispatchers.Main) {
                        onUpdate(halData)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(5000L) // Update every 5 seconds (HAL data changes less frequently)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun collectHalInterfaces(): List<HalInterface> {
        val interfaces = mutableListOf<HalInterface>()

        try {
            // Collect HAL interfaces using lshal command
            val process = Runtime.getRuntime().exec("lshal")
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            var skipHeader = true

            while (true) {
                val line = reader.readLine() ?: break
                if (skipHeader) {
                    if (line.contains("Interface") && line.contains("Transport")) {
                        skipHeader = false
                    }
                    continue
                }

                // Parse HAL interface information from lshal output
                val parts = line.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                if (parts.size >= 5) {
                    val name = parts[0]
                    val impl = parts[2]

                    // Extract version from interface name (e.g., android.hardware.audio@2.0)
                    val versionMatch = "(\\d+\\.\\d+)".toRegex().find(name)
                    val version = versionMatch?.value ?: "Unknown"

                    // Determine HAL type (HIDL/AIDL)
                    val type = if (name.contains('@')) "HIDL" else "AIDL"

                    interfaces.add(
                        HalInterface(
                            name = name,
                            version = version,
                            type = type,
                            implementation = impl,
                            status = if (parts.lastOrNull()?.contains("running") == true) "Running" else "Stopped"
                        )
                    )
                }
            }

            reader.close()
            process.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return interfaces
    }

    private suspend fun collectHwServices(): List<HwService> {
        val services = mutableListOf<HwService>()

        try {
            // Collect hardware service information using service list
            val process = Runtime.getRuntime().exec("service list")
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            while (true) {
                val line = reader.readLine() ?: break
                if (line.contains(": [")) {
                    // A line reads "12  power: [android.os.IPowerManager]", so the name is what
                    // precedes the colon once its index is dropped, and the descriptor is what sits
                    // in the brackets. The descriptor was parsed here before and then thrown away.
                    val parts = line.split(": [")
                    if (parts.size >= 2) {
                        val serviceName = parts[0].substringAfter('\t').trim()
                        val descriptor = parts[1].substringBefore(']').trim()

                        services.add(HwService(name = serviceName, interfaceDescriptor = descriptor))
                    }
                }
            }

            reader.close()
            process.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return services
    }

    /**
     * Reads `ro.vndk.version`, and reports nothing when it is unset.
     *
     * Unset is the normal answer on a current device — Android 15 removed the VNDK — so the screen
     * says that rather than showing the "30" this used to substitute silently.
     */
    private suspend fun collectVndkInfo(): VndkInfo {
        var vndkVersion: String? = null

        try {
            val versionProcess = Runtime.getRuntime().exec("getprop ro.vndk.version")
            val versionReader = BufferedReader(InputStreamReader(versionProcess.inputStream))
            val version = versionReader.readLine()
            if (!version.isNullOrBlank()) {
                vndkVersion = version
            }
            versionReader.close()
            versionProcess.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return VndkInfo(version = vndkVersion)
    }
}
