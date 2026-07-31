package com.aoscoremonitor.diagnostics.jni

import org.json.JSONArray
import org.json.JSONObject

/**
 * Why a reading is missing, as the native side classified the errno that stopped it.
 *
 * The distinction is worth carrying: a path SELinux refuses is a property of the app sandbox, and
 * one that does not exist is a property of the kernel. The screens word the two differently, and
 * neither is the same as "the value is zero".
 */
enum class Unavailable {
    /** The sandbox may not read it — EACCES or EPERM. */
    Denied,

    /** The kernel does not expose it — ENOENT and friends. */
    Absent,

    /** Read, but not usable: empty, malformed, or a failure with no better name. */
    Error;

    internal companion object {
        fun of(token: String?): Unavailable? = when (token) {
            "denied" -> Denied
            "absent" -> Absent
            "error" -> Error
            else -> null
        }
    }
}

internal fun JSONObject.unavailable(key: String): Unavailable? = Unavailable.of(stringOrNull(key))

/**
 * Reading helpers for the JSON the native collectors return.
 *
 * The native side leaves a key out when it could not take the reading, rather than sending a zero
 * or a null — a CPU whose current frequency the sandbox may not read is not a CPU running at
 * 0 kHz. These accessors carry that distinction across as a Kotlin null, which `JSONObject.optLong`
 * cannot: it answers 0 for both.
 */
internal fun JSONObject.longOrNull(key: String): Long? = if (has(key)) optLong(key) else null

internal fun JSONObject.intOrNull(key: String): Int? = if (has(key)) optInt(key) else null

internal fun JSONObject.stringOrNull(key: String): String? = if (has(key)) optString(key) else null

/** Iterates a JSON array of objects, skipping entries that are not objects. */
internal inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(transform) }

/**
 * Reads the named object's members into a map.
 *
 * Iteration order is not part of the contract — Android's `JSONObject` happens to preserve
 * insertion order and the JVM implementation the unit tests run against does not — so screens
 * name the keys they show in the order they want them rather than iterating the map.
 */
internal fun JSONObject.longMap(key: String): Map<String, Long> {
    val nested = optJSONObject(key) ?: return emptyMap()
    val values = LinkedHashMap<String, Long>()
    for (name in nested.keys()) {
        values[name] = nested.optLong(name)
    }
    return values
}

/** The same, for members that are strings. */
internal fun JSONObject.stringMap(key: String): Map<String, String> {
    val nested = optJSONObject(key) ?: return emptyMap()
    val values = LinkedHashMap<String, String>()
    for (name in nested.keys()) {
        values[name] = nested.optString(name)
    }
    return values
}
