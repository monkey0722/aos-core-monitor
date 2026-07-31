package com.aoscoremonitor.diagnostics.jni

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * One CPU core.
 *
 * Everything except [id] is nullable because sysfs exposes a different subset on every device:
 * `scaling_cur_freq` is denied to apps on some, `topology/` is absent on others. A null means the
 * reading could not be taken, which the screen states rather than papering over with a zero.
 */
data class CpuCore(
    val id: Int,
    val coreId: Int? = null,
    val packageId: Int? = null,
    val minKhz: Long? = null,
    val maxKhz: Long? = null,
    val curKhz: Long? = null,
    val frequencyUnavailable: Unavailable? = null,
    val online: Boolean = true,
    val governor: String? = null
) {
    /** Where the current frequency sits between the core's limits, for a progress indicator. */
    val frequencyFraction: Float?
        get() {
            val current = curKhz ?: return null
            val low = minKhz ?: return null
            val high = maxKhz ?: return null
            if (high <= low) return null
            return ((current - low).toFloat() / (high - low).toFloat()).coerceIn(0f, 1f)
        }
}

/** The CPU as a whole, plus the per-core readings. */
data class CpuSnapshot(
    val configuredCores: Int = 0,
    val onlineCores: Int = 0,
    val pageSize: Long = 0,
    val clockTicks: Long = 0,
    val machine: String? = null,
    val kernelRelease: String? = null,
    val cores: List<CpuCore> = emptyList(),
    val features: List<String> = emptyList()
) {
    /**
     * Folds a fresh frequency reading into the topology read once at startup.
     *
     * The two are collected separately because they change at different rates — core layout and
     * instruction set are fixed for the life of the process, while the frequency moves every few
     * milliseconds — and re-reading the fixed part every second would be work for no new data.
     */
    fun withFrequencies(readings: List<CpuCore>): CpuSnapshot {
        if (readings.isEmpty()) return this
        val byId = readings.associateBy { it.id }
        return copy(
            cores = cores.map { core ->
                val reading = byId[core.id] ?: return@map core
                core.copy(
                    curKhz = reading.curKhz,
                    frequencyUnavailable = reading.frequencyUnavailable,
                    online = reading.online,
                    governor = reading.governor
                )
            }
        )
    }
}

/**
 * Reads the CPU's shape and speed through JNI.
 *
 * The topology comes from sysfs and the instruction set from the ELF auxiliary vector, which is
 * why this is native code: `getauxval(AT_HWCAP)` has no Java equivalent, and the alternative —
 * parsing /proc/cpuinfo — returns nothing useful on most arm64 kernels.
 */
class NativeCpuInspector {

    external fun getCpuStaticNative(): String
    external fun getCpuFrequenciesNative(): String

    suspend fun readStatic(): CpuSnapshot? = withContext(Dispatchers.IO) {
        if (!NativeLibrary.isAvailable) return@withContext null
        try {
            parseCpuStatic(getCpuStaticNative())
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing CPU topology", e)
            null
        }
    }

    suspend fun readFrequencies(): List<CpuCore> = withContext(Dispatchers.IO) {
        if (!NativeLibrary.isAvailable) return@withContext emptyList()
        try {
            parseCpuFrequencies(getCpuFrequenciesNative())
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing CPU frequencies", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "NativeCpuInspector"
    }
}

/** Kept apart from the JNI call so it can be exercised without a device. */
internal fun parseCpuStatic(json: String): CpuSnapshot {
    val root = JSONObject(json)
    val cores = root.optJSONArray("cores")?.mapObjects { core ->
        CpuCore(
            id = core.optInt("id"),
            coreId = core.intOrNull("core_id"),
            packageId = core.intOrNull("package_id"),
            minKhz = core.longOrNull("min_khz"),
            maxKhz = core.longOrNull("max_khz")
        )
    }.orEmpty()

    val features = root.optJSONArray("features")?.let { array ->
        (0 until array.length()).map { array.optString(it) }.filter { it.isNotEmpty() }
    }.orEmpty()

    return CpuSnapshot(
        configuredCores = root.optInt("configured"),
        onlineCores = root.optInt("online"),
        pageSize = root.optLong("page_size"),
        clockTicks = root.optLong("clock_ticks"),
        machine = root.stringOrNull("machine"),
        kernelRelease = root.stringOrNull("kernel_release"),
        cores = cores,
        features = features
    )
}

internal fun parseCpuFrequencies(json: String): List<CpuCore> = JSONObject(json).optJSONArray("cores")?.mapObjects { core ->
    CpuCore(
        id = core.optInt("id"),
        curKhz = core.longOrNull("cur_khz"),
        frequencyUnavailable = core.unavailable("cur_khz_unavailable"),
        online = core.optBoolean("online", true),
        governor = core.stringOrNull("governor")
    )
}.orEmpty()
