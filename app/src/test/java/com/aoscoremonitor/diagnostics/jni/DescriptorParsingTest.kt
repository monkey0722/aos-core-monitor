package com.aoscoremonitor.diagnostics.jni

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DescriptorParsingTest {

    @Test
    fun readsAnOpenFile() {
        val snapshot = parseDescriptors(
            """
            {"limit_soft":32768,"limit_hard":1048576,
             "descriptors":[{"fd":12,"target":"/apex/com.android.art/javalib/core-oj.jar",
                             "type":"regular","inode":288734,"size_bytes":4194304,
                             "access":"read","flags":["nonblock"],"close_on_exec":true,"offset":0}]}
            """.trimIndent()
        )

        val descriptor = snapshot.descriptors.single()
        assertEquals(12, descriptor.fd)
        assertEquals(DescriptorKind.File, descriptor.kind)
        assertEquals(DescriptorAccess.Read, descriptor.access)
        assertEquals("core-oj.jar", descriptor.displayName)
        assertEquals("/apex/com.android.art/javalib", descriptor.directory)
        assertEquals(4_194_304L, descriptor.sizeBytes)
        assertEquals(0L, descriptor.offset)
        assertTrue(descriptor.closeOnExec)
        assertEquals(listOf("nonblock"), descriptor.flags)
    }

    @Test
    fun aSocketHasNoSizeAndNoPlaceInTheFile() {
        // The native side leaves both keys out for anything lseek reports as having no position.
        // Nothing downstream may turn that into a zero, which would read as "at the beginning".
        val snapshot = parseDescriptors(
            """{"descriptors":[{"fd":27,"target":"socket:[52831]","type":"socket","inode":52831,"access":"readwrite"}]}"""
        )

        val descriptor = snapshot.descriptors.single()
        assertEquals(DescriptorKind.Socket, descriptor.kind)
        assertNull(descriptor.offset)
        assertNull(descriptor.sizeBytes)
        assertEquals("socket:[52831]", descriptor.displayName)
        assertEquals("", descriptor.directory)
    }

    @Test
    fun aShortPathIsShownWholeRatherThanSplit() {
        // Splitting /dev/null leaves a card headed "null" over a line reading "/dev", which is two
        // lines to say what fitted on one.
        val snapshot = parseDescriptors("""{"descriptors":[{"fd":0,"target":"/dev/null","type":"character"}]}""")

        val descriptor = snapshot.descriptors.single()
        assertEquals("/dev/null", descriptor.displayName)
        assertEquals("", descriptor.directory)
    }

    @Test
    fun aKernelObjectIsToldApartByItsTargetRatherThanByItsType() {
        // An eventfd is S_IFREG to fstat, exactly as an APK is. Only the link text separates them,
        // which is why the kind is decided here rather than from the mode bits alone.
        val snapshot = parseDescriptors(
            """{"descriptors":[{"fd":31,"target":"anon_inode:[eventfd]","type":"regular","access":"readwrite"}]}"""
        )

        assertEquals(DescriptorKind.AnonInode, snapshot.descriptors.single().kind)
    }

    @Test
    fun deviceNodesAreKeptApartFromFiles() {
        val snapshot = parseDescriptors(
            """
            {"descriptors":[{"fd":0,"target":"/dev/null","type":"character"},
                            {"fd":3,"target":"/dev/block/dm-9","type":"block"},
                            {"fd":4,"target":"pipe:[9182]","type":"fifo"},
                            {"fd":5,"target":"/data/user/0/com.aoscoremonitor","type":"directory"}]}
            """.trimIndent()
        )

        assertEquals(
            listOf(DescriptorKind.Device, DescriptorKind.Device, DescriptorKind.Pipe, DescriptorKind.Directory),
            snapshot.descriptors.map { it.kind }
        )
    }

    @Test
    fun descriptorsAreOrderedByNumber() {
        val snapshot = parseDescriptors(
            """{"descriptors":[{"fd":31,"target":"x"},{"fd":2,"target":"y"},{"fd":12,"target":"z"}]}"""
        )

        assertEquals(listOf(2, 12, 31), snapshot.descriptors.map { it.fd })
    }

    @Test
    fun theBreakdownCountsEachKindOnce() {
        val snapshot = parseDescriptors(
            """
            {"descriptors":[{"fd":0,"target":"/dev/null","type":"character"},
                            {"fd":1,"target":"/dev/null","type":"character"},
                            {"fd":9,"target":"socket:[1]","type":"socket"}]}
            """.trimIndent()
        )

        // In the kinds' own declared order rather than by count, so that a descriptor opening or
        // closing does not reshuffle the row of counts under the reader.
        assertEquals(listOf(DescriptorKind.Socket to 1, DescriptorKind.Device to 2), snapshot.breakdown)
    }

    @Test
    fun headroomIsMeasuredAgainstTheSoftLimit() {
        val snapshot = parseDescriptors(
            """{"limit_soft":8,"descriptors":[{"fd":0,"target":"a"},{"fd":1,"target":"b"}]}"""
        )

        assertEquals(2, snapshot.count)
        assertEquals(0.25f, snapshot.usedFraction)
    }

    @Test
    fun anUnboundedLimitIsNotDrawnAsAnEmptyBar() {
        // The native side omits a limit of RLIM_INFINITY rather than sending a sentinel, so there
        // is nothing here to draw a fraction against.
        val snapshot = parseDescriptors("""{"descriptors":[{"fd":0,"target":"a"}]}""")

        assertNull(snapshot.softLimit)
        assertNull(snapshot.usedFraction)
    }

    @Test
    fun aDescriptorWhoseTargetWasRefusedKeepsItsNumberAndSaysWhy() {
        val snapshot = parseDescriptors(
            """{"descriptors":[{"fd":6,"target_unavailable":"denied","type":"regular"}]}"""
        )

        val descriptor = snapshot.descriptors.single()
        assertEquals(6, descriptor.fd)
        assertEquals(Unavailable.Denied, descriptor.targetUnavailable)
        assertEquals("", descriptor.displayName)
    }
}
