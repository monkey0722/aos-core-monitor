package com.aoscoremonitor.diagnostics.jni

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * A shared object the dynamic linker has mapped into this process.
 *
 * @param path as the linker reports it. Empty for the main executable, which the linker names
 *   with an empty string rather than a path.
 * @param buildId the GNU build id, which ties the mapped library back to the symbols the platform
 *   build produced. Null when the object was linked without one.
 */
data class LoadedModule(
    val path: String,
    val baseAddress: String,
    val mappedSize: Long,
    val buildId: String? = null,
    val hasRelro: Boolean = false,
    val hasTls: Boolean = false,
    val segmentCount: Int = 0
) {
    /** The file name on its own, which is what identifies a library at a glance. */
    val fileName: String get() = path.substringAfterLast('/')

    /** The directory it was loaded from — /apex, /system, /vendor or the app's own lib dir. */
    val directory: String get() = path.substringBeforeLast('/', missingDelimiterValue = "")

    /** The linker reports the main executable with an empty name. */
    val isMainExecutable: Boolean get() = path.isEmpty()
}

/** The loaded set, and how much churn the linker has seen. */
data class ModuleSnapshot(val modules: List<LoadedModule> = emptyList(), val loadEvents: Int? = null, val unloadEvents: Int? = null) {
    val totalMappedSize: Long get() = modules.sumOf { it.mappedSize }
}

/**
 * Lists what the dynamic linker has loaded.
 *
 * This is the reading that most needs native code: the linker publishes its list through
 * `dl_iterate_phdr` and nowhere else. Nothing in the Java API can name the mapped libraries, let
 * alone their load addresses or build ids.
 */
class NativeModuleInspector {

    external fun getLoadedModulesNative(): String

    suspend fun read(): ModuleSnapshot? = withContext(Dispatchers.IO) {
        if (!NativeLibrary.isAvailable) return@withContext null
        try {
            parseLoadedModules(getLoadedModulesNative())
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing loaded modules", e)
            null
        }
    }

    private companion object {
        const val TAG = "NativeModuleInspector"
    }
}

internal fun parseLoadedModules(json: String): ModuleSnapshot {
    val root = JSONObject(json)
    val modules = root.optJSONArray("modules")?.mapObjects { module ->
        LoadedModule(
            path = module.optString("path"),
            baseAddress = module.optString("base"),
            mappedSize = module.optLong("mapped_size"),
            buildId = module.stringOrNull("build_id"),
            hasRelro = module.optBoolean("relro"),
            hasTls = module.optBoolean("tls"),
            segmentCount = module.optInt("segment_count")
        )
    }.orEmpty()

    return ModuleSnapshot(
        // Largest first: the libraries worth noticing are the ones holding the most address space,
        // and the linker's own order is load order, which says nothing the screen can use.
        modules = modules.sortedByDescending { it.mappedSize },
        loadEvents = root.intOrNull("adds"),
        unloadEvents = root.intOrNull("subs")
    )
}
