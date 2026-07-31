package com.aoscoremonitor.diagnostics.jni

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MountParsingTest {

    @Test
    fun readsAMeasuredFilesystem() {
        val mounts = parseMounts(
            """
            {"mounts":[{"source":"/dev/block/dm-9","target":"/data","fs_type":"f2fs",
                        "options":"rw,nosuid,nodev,noatime","readonly":false,"statvfs_ok":true,
                        "block_size":4096,"total_bytes":100,"free_bytes":40,"available_bytes":30,
                        "inodes_total":1000,"inodes_free":900}]}
            """.trimIndent()
        )

        val mount = mounts.single()
        assertEquals("/data", mount.target)
        assertEquals(60L, mount.usedBytes)
        assertEquals(0.6f, mount.usedFraction)
        assertTrue(mount.isRealFilesystem)
        assertFalse(mount.readOnly)
    }

    @Test
    fun aMountStatvfsRefusedKeepsItsIdentityAndLosesItsCapacity() {
        val mounts = parseMounts(
            """{"mounts":[{"source":"proc","target":"/proc","fs_type":"proc","options":"rw","readonly":false,"statvfs_ok":false}]}"""
        )

        val mount = mounts.single()
        assertEquals("/proc", mount.target)
        assertNull(mount.totalBytes)
        assertNull(mount.usedBytes)
        assertNull(mount.usedFraction)
        assertFalse(mount.isRealFilesystem)
    }

    @Test
    fun capacityIsIgnoredWhenTheCallDidNotSucceed() {
        // Should the native side ever send sizes alongside a failed statvfs, they are not readings
        // and must not reach the screen as though they were.
        val mounts = parseMounts(
            """{"mounts":[{"source":"x","target":"/x","fs_type":"f2fs","options":"rw","readonly":false,"statvfs_ok":false,"total_bytes":100,"free_bytes":40}]}"""
        )

        assertNull(mounts.single().totalBytes)
    }

    @Test
    fun aFilesystemWithoutAnInodeTableReportsNoCount() {
        // erofs and f2fs answer statvfs with every bit set rather than a count, so the native side
        // omits the keys entirely. Nothing downstream may turn that into a number.
        val mounts = parseMounts(
            """{"mounts":[{"source":"/dev/block/dm-0","target":"/","fs_type":"erofs","options":"ro","readonly":true,"statvfs_ok":true,"total_bytes":100,"free_bytes":0}]}"""
        )

        val mount = mounts.single()
        assertNull(mount.inodesTotal)
        assertNull(mount.inodesFree)
        assertEquals(100L, mount.totalBytes)
    }

    @Test
    fun readOnlyMountsAreMarked() {
        val mounts = parseMounts(
            """{"mounts":[{"source":"/dev/block/dm-0","target":"/","fs_type":"erofs","options":"ro,seclabel","readonly":true,"statvfs_ok":false}]}"""
        )

        assertTrue(mounts.single().readOnly)
    }
}
