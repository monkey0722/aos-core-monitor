package com.aoscoremonitor.diagnostics.jni

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What kind of device the driver says it is. */
enum class VulkanDeviceType {
    IntegratedGpu,
    DiscreteGpu,
    VirtualGpu,
    Cpu,
    Other,

    /** A type this version of Vulkan did not define when the app was built. */
    Unknown;

    internal companion object {
        fun of(token: String?): VulkanDeviceType = when (token) {
            "integrated_gpu" -> IntegratedGpu
            "discrete_gpu" -> DiscreteGpu
            "virtual_gpu" -> VirtualGpu
            "cpu" -> Cpu
            "other" -> Other
            else -> Unknown
        }
    }
}

/**
 * A pool of device memory, and whether it is the GPU's own.
 *
 * A phone reports one heap that is both — the GPU and the CPU share the same physical memory — so
 * a second heap on a handset is worth noticing rather than assuming.
 */
data class VulkanMemoryHeap(val index: Int, val sizeBytes: Long, val isDeviceLocal: Boolean)

/** One way of allocating out of a heap, as the combination of properties it carries. */
data class VulkanMemoryType(
    val index: Int,
    val heapIndex: Int,
    val isDeviceLocal: Boolean = false,
    val isHostVisible: Boolean = false,
    val isHostCoherent: Boolean = false,
    val isHostCached: Boolean = false,
    val isLazilyAllocated: Boolean = false,
    val isProtected: Boolean = false
)

/** A group of interchangeable queues, and the work they accept. */
data class VulkanQueueFamily(
    val index: Int,
    val queueCount: Int,
    val graphics: Boolean = false,
    val compute: Boolean = false,
    val transfer: Boolean = false,
    val sparseBinding: Boolean = false,
    val protectedMemory: Boolean = false,
    val timestampBits: Int = 0
)

/**
 * One physical device, as its driver describes it.
 *
 * @param driverId the driver behind the device, as the enum value that identifies it. Null where
 *   the driver reported none — see [driverQueryAvailable] for the two reasons that happens.
 * @param driverIdName the same id, named. Null where [driverId] is, and also where the id is one
 *   this app has no name for — the number is still there.
 * @param driverQueryAvailable whether this device could be asked which driver is behind it at all.
 *   The query needs Vulkan 1.2 or the `VK_KHR_driver_properties` extension, both of which are
 *   properties of this device rather than of the instance, so two devices in one process can
 *   differ. False means [driverId] is absent because nothing was asked.
 * @param driverVersionRaw the driver's own version number, unpacked. Its encoding is the vendor's
 *   choice, so [driverVersion] — the conventional split — is a reading of it rather than a fact
 *   about it.
 */
data class VulkanDevice(
    val name: String,
    val type: VulkanDeviceType,
    val apiVersion: String,
    val driverVersion: String,
    val driverVersionRaw: Long,
    val vendorId: String,
    val deviceId: String,
    val pipelineCacheUuid: String? = null,
    val driverId: Int? = null,
    val driverIdName: String? = null,
    val driverQueryAvailable: Boolean = false,
    val driverName: String? = null,
    val driverInfo: String? = null,
    val conformanceVersion: String? = null,
    val limits: Map<String, Long> = emptyMap(),
    val memoryHeaps: List<VulkanMemoryHeap> = emptyList(),
    val memoryTypes: List<VulkanMemoryType> = emptyList(),
    val queueFamilies: List<VulkanQueueFamily> = emptyList(),
    val extensions: List<String> = emptyList()
) {
    /** The total the heaps add up to, which is what the device can allocate at all. */
    val totalMemoryBytes: Long get() = memoryHeaps.sumOf { it.sizeBytes }
}

/**
 * What Vulkan reported, or how far the collector got before it could not.
 *
 * The three ways this comes back empty say different things, and the screen words them
 * differently: no loader on the device at all, a loader with no driver behind it, and a driver that
 * started but enumerated nothing.
 *
 * @param instanceError the failure that stopped `vkCreateInstance`, named where it is one this app
 *   knows. Null when the instance was created.
 * @param instanceErrorCode the same failure as its raw `VkResult`, carried only when the name is not
 *   known.
 */
data class VulkanSnapshot(
    val loaderPresent: Boolean = false,
    val instanceVersion: String? = null,
    val instanceCreated: Boolean = false,
    val instanceError: String? = null,
    val instanceErrorCode: Int? = null,
    val devices: List<VulkanDevice> = emptyList()
)

/**
 * Asks Vulkan what GPUs this device has.
 *
 * Vulkan is a C API with no Java binding on Android, so every reading here is one the framework
 * cannot take: the driver id, the version the driver passed conformance at, the memory heaps, the
 * queue families. What Java can reach is the GLES renderer string, which is prose.
 */
class NativeVulkanInspector {

    external fun getVulkanNative(): String

    suspend fun read(): VulkanSnapshot? = withContext(Dispatchers.IO) {
        if (!NativeLibrary.isAvailable) return@withContext null
        try {
            parseVulkan(getVulkanNative())
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Vulkan properties", e)
            null
        }
    }

    private companion object {
        const val TAG = "NativeVulkanInspector"
    }
}

/**
 * The reading, or null where the collector produced none.
 *
 * A collector that threw hands back an empty object, and every field of a snapshot built from one
 * would be a default that reads as a statement about the device — `loaderPresent = false` says this
 * phone has no Vulkan loader, which is a claim the app would be making about its own failure. The
 * loader is the one thing the collector reports whatever else happened, so its absence is how an
 * empty document is told apart from a device with nothing to report.
 */
internal fun parseVulkan(json: String): VulkanSnapshot? {
    val root = JSONObject(json)
    if (!root.has("loader_present")) return null

    val devices = root.optJSONArray("devices")?.mapObjects { device ->
        VulkanDevice(
            name = device.optString("name"),
            type = VulkanDeviceType.of(device.stringOrNull("type")),
            apiVersion = device.optString("api_version"),
            driverVersion = device.optString("driver_version"),
            driverVersionRaw = device.optLong("driver_version_raw"),
            vendorId = device.optString("vendor_id"),
            deviceId = device.optString("device_id"),
            pipelineCacheUuid = device.stringOrNull("pipeline_cache_uuid"),
            driverId = device.intOrNull("driver_id"),
            driverIdName = device.stringOrNull("driver_id_name"),
            driverQueryAvailable = device.optBoolean("driver_query_available"),
            // Blank is not a name: a driver that fills the structure with an empty string has
            // reported nothing, and a row rendered from one is an empty row under a label.
            driverName = device.stringOrNull("driver_name")?.takeIf { it.isNotBlank() },
            driverInfo = device.stringOrNull("driver_info")?.takeIf { it.isNotBlank() },
            conformanceVersion = device.stringOrNull("conformance_version"),
            limits = device.longMap("limits"),
            memoryHeaps = device.optJSONArray("memory_heaps")?.mapObjects { heap ->
                VulkanMemoryHeap(
                    index = heap.optInt("index"),
                    sizeBytes = heap.optLong("size_bytes"),
                    isDeviceLocal = heap.optBoolean("device_local")
                )
            }.orEmpty(),
            memoryTypes = device.optJSONArray("memory_types")?.mapObjects { type ->
                VulkanMemoryType(
                    index = type.optInt("index"),
                    heapIndex = type.optInt("heap"),
                    isDeviceLocal = type.optBoolean("device_local"),
                    isHostVisible = type.optBoolean("host_visible"),
                    isHostCoherent = type.optBoolean("host_coherent"),
                    isHostCached = type.optBoolean("host_cached"),
                    isLazilyAllocated = type.optBoolean("lazily_allocated"),
                    isProtected = type.optBoolean("protected_memory")
                )
            }.orEmpty(),
            queueFamilies = device.optJSONArray("queue_families")?.mapObjects { family ->
                VulkanQueueFamily(
                    index = family.optInt("index"),
                    queueCount = family.optInt("count"),
                    graphics = family.optBoolean("graphics"),
                    compute = family.optBoolean("compute"),
                    transfer = family.optBoolean("transfer"),
                    sparseBinding = family.optBoolean("sparse_binding"),
                    protectedMemory = family.optBoolean("protected_memory"),
                    timestampBits = family.optInt("timestamp_bits")
                )
            }.orEmpty(),
            // Sorted because the driver's own order is not one: the list is what the device
            // supports, and a name is how anyone looks for an entry in it.
            extensions = device.optJSONArray("extensions")
                ?.let { array -> (0 until array.length()).map(array::optString) }
                .orEmpty()
                .sorted()
        )
    }.orEmpty()

    return VulkanSnapshot(
        loaderPresent = root.optBoolean("loader_present"),
        instanceVersion = root.stringOrNull("instance_version"),
        instanceCreated = root.optBoolean("instance_ok"),
        instanceError = root.stringOrNull("instance_error"),
        instanceErrorCode = root.intOrNull("instance_error_code"),
        devices = devices
    )
}
