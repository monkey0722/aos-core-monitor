package com.aoscoremonitor.diagnostics.jni

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * One entry from the process's mount table.
 *
 * The capacity fields are null when `statvfs` was refused, which happens for /proc, /sys and most
 * of /mnt from inside the app sandbox. The mount is still worth listing — that it exists is part
 * of the picture — so it is kept with its capacity left unknown rather than dropped.
 */
data class MountPoint(
    val source: String,
    val target: String,
    val fsType: String,
    val options: String,
    val readOnly: Boolean,
    val totalBytes: Long? = null,
    val freeBytes: Long? = null,
    val availableBytes: Long? = null,
    val inodesTotal: Long? = null,
    val inodesFree: Long? = null
) {
    /**
     * Space in use.
     *
     * Measured against the free total rather than the available one: the difference is the reserve
     * the filesystem keeps for root, which is in use by nobody and should not be counted as used.
     */
    val usedBytes: Long?
        get() {
            val total = totalBytes ?: return null
            val free = freeBytes ?: return null
            return (total - free).coerceAtLeast(0)
        }

    val usedFraction: Float?
        get() {
            val total = totalBytes ?: return null
            val used = usedBytes ?: return null
            if (total <= 0) return null
            return (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }

    /**
     * Whether this holds files, as opposed to being a kernel interface mounted like a filesystem.
     *
     * The split is made here rather than in the native collector so that changing what counts as
     * interesting does not mean rebuilding the library. An Android mount table has upwards of a
     * hundred entries and all but a handful are pseudo filesystems, so the screen would otherwise
     * bury /data under cgroup mounts.
     */
    val isRealFilesystem: Boolean get() = fsType in REAL_FILESYSTEMS

    private companion object {
        val REAL_FILESYSTEMS = setOf(
            "f2fs", "ext2", "ext3", "ext4", "erofs", "squashfs", "vfat", "exfat", "ntfs", "ntfs3",
            "fuse", "fuseblk", "sdcardfs", "overlay", "incremental-fs"
        )
    }
}

/**
 * Reads the mount table and the space left on each filesystem.
 *
 * `/proc/self/mounts` describes the caller's own mount namespace, which keeps it readable where
 * the system-wide /proc files this app also reads are denied. `statvfs` is a syscall with no
 * Java counterpart that reports free space for an arbitrary path — `File.getFreeSpace` covers only
 * paths the app can already see, and says nothing about filesystem type, options or inodes.
 */
class NativeStorageInspector {

    external fun getMountsNative(): String

    suspend fun read(): List<MountPoint>? = withContext(Dispatchers.IO) {
        if (!NativeLibrary.isAvailable) return@withContext null
        try {
            parseMounts(getMountsNative())
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing mount table", e)
            null
        }
    }

    private companion object {
        const val TAG = "NativeStorageInspector"
    }
}

internal fun parseMounts(json: String): List<MountPoint> = JSONObject(json).optJSONArray("mounts")?.mapObjects { mount ->
    val measured = mount.optBoolean("statvfs_ok")
    MountPoint(
        source = mount.optString("source"),
        target = mount.optString("target"),
        fsType = mount.optString("fs_type"),
        options = mount.optString("options"),
        readOnly = mount.optBoolean("readonly"),
        totalBytes = if (measured) mount.longOrNull("total_bytes") else null,
        freeBytes = if (measured) mount.longOrNull("free_bytes") else null,
        availableBytes = if (measured) mount.longOrNull("available_bytes") else null,
        inodesTotal = if (measured) mount.longOrNull("inodes_total") else null,
        inodesFree = if (measured) mount.longOrNull("inodes_free") else null
    )
}.orEmpty()
