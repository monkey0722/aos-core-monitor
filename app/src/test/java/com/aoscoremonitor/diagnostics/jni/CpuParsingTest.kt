package com.aoscoremonitor.diagnostics.jni

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the half of the CPU collector that can be tested without a device.
 *
 * The point of these is the absent-key contract: sysfs exposes a different subset on every device,
 * and the native side leaves a key out rather than sending a zero. A parser that turned those into
 * zeros would have the screen report a core running at 0 MHz between limits of 0 and 0.
 */
class CpuParsingTest {

    @Test
    fun readsTopologyAndFeatures() {
        val snapshot = parseCpuStatic(
            """
            {"configured":8,"online":6,"page_size":4096,"clock_ticks":100,
             "machine":"aarch64","kernel_release":"5.15.123-android14",
             "cores":[{"id":0,"core_id":0,"package_id":0,"min_khz":300000,"max_khz":1804800}],
             "features":["fp","asimd","sve"]}
            """.trimIndent()
        )

        assertEquals(8, snapshot.configuredCores)
        assertEquals(6, snapshot.onlineCores)
        assertEquals("aarch64", snapshot.machine)
        assertEquals(listOf("fp", "asimd", "sve"), snapshot.features)
        assertEquals(1, snapshot.cores.size)
        assertEquals(1_804_800L, snapshot.cores.first().maxKhz)
    }

    @Test
    fun missingReadingsStayNullRatherThanBecomingZero() {
        val snapshot = parseCpuStatic("""{"configured":1,"cores":[{"id":0}],"features":[]}""")

        val core = snapshot.cores.single()
        assertNull(core.minKhz)
        assertNull(core.maxKhz)
        assertNull(core.coreId)
        assertNull(core.packageId)
        assertNull(snapshot.machine)
    }

    @Test
    fun frequencyReadingIsFoldedIntoTheTopology() {
        val topology = parseCpuStatic(
            """{"configured":2,"cores":[{"id":0,"min_khz":300000,"max_khz":1800000},{"id":1}],"features":[]}"""
        )
        val readings = parseCpuFrequencies(
            """{"cores":[{"id":0,"online":true,"cur_khz":900000,"governor":"schedutil"},{"id":1,"online":false}]}"""
        )

        val merged = topology.withFrequencies(readings)

        val first = merged.cores.first()
        assertEquals(900_000L, first.curKhz)
        assertEquals("schedutil", first.governor)
        // The limits came from the topology read and must survive the merge.
        assertEquals(300_000L, first.minKhz)
        assertTrue(first.online)
        assertEquals(false, merged.cores[1].online)
        assertNull(merged.cores[1].curKhz)
    }

    @Test
    fun aMissingFrequencyCarriesTheReasonItIsMissing() {
        val cores = parseCpuFrequencies(
            """
            {"cores":[{"id":0,"online":true,"cur_khz_unavailable":"denied"},
                      {"id":1,"online":true,"cur_khz_unavailable":"absent"},
                      {"id":2,"online":true,"cur_khz":900000}]}
            """.trimIndent()
        )

        assertEquals(Unavailable.Denied, cores[0].frequencyUnavailable)
        assertEquals(Unavailable.Absent, cores[1].frequencyUnavailable)
        // A reading that succeeded has no reason attached to it.
        assertNull(cores[2].frequencyUnavailable)
    }

    @Test
    fun anUnknownReasonIsNoReasonRatherThanACrash() {
        val cores = parseCpuFrequencies("""{"cores":[{"id":0,"cur_khz_unavailable":"something new"}]}""")

        assertNull(cores.single().frequencyUnavailable)
    }

    @Test
    fun theReasonSurvivesTheMergeIntoTheTopology() {
        val topology = parseCpuStatic("""{"configured":1,"cores":[{"id":0}],"features":[]}""")
        val merged = topology.withFrequencies(
            parseCpuFrequencies("""{"cores":[{"id":0,"cur_khz_unavailable":"denied"}]}""")
        )

        assertEquals(Unavailable.Denied, merged.cores.single().frequencyUnavailable)
    }

    @Test
    fun frequencyFractionNeedsBothLimitsAndAReading() {
        assertEquals(0.5f, CpuCore(id = 0, minKhz = 0, maxKhz = 2000, curKhz = 1000).frequencyFraction)
        assertNull(CpuCore(id = 0, minKhz = 0, maxKhz = 2000).frequencyFraction)
        assertNull(CpuCore(id = 0, curKhz = 1000).frequencyFraction)
        // A core whose limits are equal would divide by zero.
        assertNull(CpuCore(id = 0, minKhz = 1000, maxKhz = 1000, curKhz = 1000).frequencyFraction)
    }
}
