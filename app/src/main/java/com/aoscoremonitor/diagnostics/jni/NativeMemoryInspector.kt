package com.aoscoremonitor.diagnostics.jni

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The process's resident footprint, summed over every mapping it owns.
 *
 * [fromRollupFile] records which file the numbers came from: the kernel's own `smaps_rollup`
 * summary, or the per-mapping `smaps` walked and totalled by hand where that file is missing. The
 * values mean the same thing either way, but the second is a much longer read and worth naming.
 */
data class MemoryRollup(
    val rssKb: Long,
    val pssKb: Long,
    val privateCleanKb: Long,
    val privateDirtyKb: Long,
    val sharedCleanKb: Long,
    val sharedDirtyKb: Long,
    val swapKb: Long,
    val swapPssKb: Long,
    val fromRollupFile: Boolean
)

/** How much of the address space is given over to one kind of mapping. */
data class RegionCategory(val key: String, val count: Int, val sizeKb: Long)

/**
 * Everything the memory screen shows, from one pass over /proc/self.
 *
 * [reservedKb] is address space mapped with no access at all — the CFI shadow, WebView's
 * reservation, the allocator's reserve — which is most of a 64-bit process's address space and
 * holds nothing. It is reported on its own rather than mixed into [categories], where it would
 * dwarf every category that describes actual memory.
 */
data class MemorySnapshot(
    val rollup: MemoryRollup? = null,
    val status: Map<String, String> = emptyMap(),
    val totalRegions: Int = 0,
    val reservedRegions: Int = 0,
    val reservedKb: Long = 0,
    val categories: List<RegionCategory> = emptyList(),
    val malloc: Map<String, Long> = emptyMap(),
    val limits: Map<String, Long> = emptyMap()
) {
    /** The accessible total the category sizes are shares of. */
    val mappedKb: Long get() = categories.sumOf { it.sizeKb }
}

/**
 * Reads this process's own address space.
 *
 * Unlike the system-wide counters the older native screen reads, none of this can be denied: a
 * process may always read its own /proc entries. The native heap figures come from `mallinfo2`,
 * which is inside libc and has no Java equivalent at all.
 */
class NativeMemoryInspector {

    external fun getMemoryMapNative(): String

    suspend fun read(): MemorySnapshot? = withContext(Dispatchers.IO) {
        if (!NativeLibrary.isAvailable) return@withContext null
        try {
            parseMemoryMap(getMemoryMapNative())
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing memory map", e)
            null
        }
    }

    private companion object {
        const val TAG = "NativeMemoryInspector"
    }
}

internal fun parseMemoryMap(json: String): MemorySnapshot {
    val root = JSONObject(json)

    val rollup = root.optJSONObject("rollup")?.let { values ->
        MemoryRollup(
            rssKb = values.optLong("rss_kb"),
            pssKb = values.optLong("pss_kb"),
            privateCleanKb = values.optLong("private_clean_kb"),
            privateDirtyKb = values.optLong("private_dirty_kb"),
            sharedCleanKb = values.optLong("shared_clean_kb"),
            sharedDirtyKb = values.optLong("shared_dirty_kb"),
            swapKb = values.optLong("swap_kb"),
            swapPssKb = values.optLong("swap_pss_kb"),
            fromRollupFile = values.optBoolean("from_rollup_file")
        )
    }

    val regions = root.optJSONObject("regions")
    val categories = regions?.optJSONArray("categories")?.mapObjects { category ->
        RegionCategory(
            key = category.optString("key"),
            count = category.optInt("count"),
            sizeKb = category.optLong("size_kb")
        )
    }.orEmpty()

    return MemorySnapshot(
        rollup = rollup,
        status = root.stringMap("status"),
        totalRegions = regions?.optInt("total") ?: 0,
        reservedRegions = regions?.optInt("reserved_count") ?: 0,
        reservedKb = regions?.optLong("reserved_kb") ?: 0,
        categories = categories.sortedByDescending { it.sizeKb },
        malloc = root.longMap("malloc"),
        limits = root.longMap("limits")
    )
}
