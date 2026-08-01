package com.aoscoremonitor.diagnostics.jni

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * What a descriptor is open on.
 *
 * Derived here rather than sent by the native collector: the kernel reports a type and a link
 * target, and which of those matters differs by case — an eventfd and a regular file are both
 * `S_IFREG` to `fstat` and are told apart only by the target text, while a socket is a socket
 * whatever its target says. Deciding it here also means changing the grouping does not mean
 * rebuilding the library.
 */
enum class DescriptorKind {
    /** An ordinary file: an APK, a font, a dex the runtime has mapped. */
    File,
    Directory,
    Socket,

    /** A pipe, named or otherwise. */
    Pipe,

    /** eventfd, epoll, timerfd, dmabuf — a kernel object with no name in any filesystem. */
    AnonInode,

    /** A character or block device: /dev/binder, /dev/null, /dev/ashmem. */
    Device,
    Other
}

/** How the file was opened, from the access mode the descriptor carries. */
enum class DescriptorAccess {
    Read,
    Write,
    ReadWrite,
    Unknown;

    internal companion object {
        fun of(token: String?): DescriptorAccess = when (token) {
            "read" -> Read
            "write" -> Write
            "readwrite" -> ReadWrite
            else -> Unknown
        }
    }
}

/**
 * One open descriptor.
 *
 * [target] is the link text from `/proc/self/fd`, which is a path only some of the time: a socket
 * reads as `socket:[12345]` and an eventfd as `anon_inode:[eventfd]`. It is kept verbatim because
 * those forms are what identifies the object, and [targetUnavailable] says why it is empty when the
 * link could not be read at all.
 *
 * [offset] is null for anything with no position to be at — a socket, a pipe — rather than zero,
 * which would read as "at the beginning".
 */
data class OpenDescriptor(
    val fd: Int,
    val target: String = "",
    val targetUnavailable: Unavailable? = null,
    val type: String? = null,
    val typeUnavailable: Unavailable? = null,
    val access: DescriptorAccess = DescriptorAccess.Unknown,
    val flags: List<String> = emptyList(),
    val closeOnExec: Boolean = false,
    val inode: Long? = null,
    val sizeBytes: Long? = null,
    val offset: Long? = null
) {
    val kind: DescriptorKind
        get() = when {
            target.startsWith(ANON_INODE_PREFIX) -> DescriptorKind.AnonInode
            type == "socket" -> DescriptorKind.Socket
            type == "fifo" -> DescriptorKind.Pipe
            type == "directory" -> DescriptorKind.Directory
            type == "character" || type == "block" -> DescriptorKind.Device
            type == "regular" -> DescriptorKind.File
            else -> DescriptorKind.Other
        }

    /**
     * The short label for the list.
     *
     * A long path is shown by its file name, because the process holds a dozen descriptors under
     * /apex/com.android.art/javalib and the directory is the same for all of them. A short one is
     * shown whole: splitting /dev/null leaves a card headed "null" over a line reading "/dev",
     * which is two lines to say what fitted on one.
     *
     * Anything that is not a path — `socket:[12345]`, `anon_inode:[eventfd]` — has no file name to
     * split off and is always shown whole.
     */
    val displayName: String get() = if (isSplit) target.substringAfterLast('/') else target

    /** The directory a long path was opened from, and empty for anything shown whole. */
    val directory: String get() = if (isSplit) target.substringBeforeLast('/', "") else ""

    private val isSplit: Boolean get() = target.startsWith('/') && target.length > SHORT_PATH

    private companion object {
        const val ANON_INODE_PREFIX = "anon_inode:"

        /** Roughly what fits on one line of a card beside the two tags, on a phone. */
        const val SHORT_PATH = 24
    }
}

/**
 * The descriptor table, and the ceiling it is filling up against.
 *
 * [softLimit] is null when the limit is unbounded, which the native side leaves out rather than
 * sending a sentinel that would be drawn as a nearly-empty bar.
 */
data class DescriptorSnapshot(
    val descriptors: List<OpenDescriptor> = emptyList(),
    val softLimit: Long? = null,
    val hardLimit: Long? = null
) {
    val count: Int get() = descriptors.size

    /** How much of the soft limit is spent, for a headroom bar. */
    val usedFraction: Float?
        get() {
            val limit = softLimit ?: return null
            if (limit <= 0) return null
            return (count.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
        }

    /** How many descriptors of each kind, in a fixed order so the breakdown does not reshuffle. */
    val breakdown: List<Pair<DescriptorKind, Int>>
        get() = DescriptorKind.entries
            .map { kind -> kind to descriptors.count { it.kind == kind } }
            .filter { (_, count) -> count > 0 }
}

/**
 * Lists what this process has open.
 *
 * Java can name the numbers in `/proc/self/fd` and go no further. The target of each needs
 * `readlink` — `File.getCanonicalPath` throws on `socket:[12345]`, which is not a path — the kind
 * needs `fstat`, the access mode and close-on-exec flag need `fcntl`, and the file offset needs
 * `lseek`. None of the four has a counterpart in the Java API.
 */
class NativeDescriptorInspector {

    external fun getDescriptorsNative(): String

    suspend fun read(): DescriptorSnapshot? = withContext(Dispatchers.IO) {
        if (!NativeLibrary.isAvailable) return@withContext null
        try {
            parseDescriptors(getDescriptorsNative())
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing descriptor list", e)
            null
        }
    }

    private companion object {
        const val TAG = "NativeDescriptorInspector"
    }
}

/** Kept apart from the JNI call so it can be exercised without a device. */
internal fun parseDescriptors(json: String): DescriptorSnapshot {
    val root = JSONObject(json)

    val descriptors = root.optJSONArray("descriptors")?.mapObjects { descriptor ->
        val flags = descriptor.optJSONArray("flags")?.let { array ->
            (0 until array.length()).map { array.optString(it) }.filter { it.isNotEmpty() }
        }.orEmpty()

        OpenDescriptor(
            fd = descriptor.optInt("fd"),
            target = descriptor.stringOrNull("target").orEmpty(),
            targetUnavailable = descriptor.unavailable("target_unavailable"),
            type = descriptor.stringOrNull("type"),
            typeUnavailable = descriptor.unavailable("stat_unavailable"),
            access = DescriptorAccess.of(descriptor.stringOrNull("access")),
            flags = flags,
            closeOnExec = descriptor.optBoolean("close_on_exec"),
            inode = descriptor.longOrNull("inode"),
            sizeBytes = descriptor.longOrNull("size_bytes"),
            offset = descriptor.longOrNull("offset")
        )
    }.orEmpty()

    return DescriptorSnapshot(
        // By number, which is the order the kernel hands them out in and the only order that says
        // anything: 0, 1 and 2 are the standard streams on every process, and a high number is a
        // descriptor opened late.
        descriptors = descriptors.sortedBy { it.fd },
        softLimit = root.longOrNull("limit_soft"),
        hardLimit = root.longOrNull("limit_hard")
    )
}
