package com.aoscoremonitor.diagnostics.jni

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryMapParsingTest {

    @Test
    fun readsTheRollupAndTheCategories() {
        val snapshot = parseMemoryMap(
            """
            {"rollup":{"rss_kb":98304,"pss_kb":74120,"private_clean_kb":4096,"private_dirty_kb":51200,
                       "shared_clean_kb":40960,"shared_dirty_kb":2048,"swap_kb":0,"swap_pss_kb":0,
                       "from_rollup_file":true},
             "status":{"VmRSS":"98304 kB","Threads":"21"},
             "regions":{"total":3,"categories":[{"key":"anon","count":2,"size_kb":1024},
                                                {"key":"native_lib","count":1,"size_kb":8192}]},
             "malloc":{"arena":8388608,"in_use":6291456},
             "limits":{"address_space":137438953472}}
            """.trimIndent()
        )

        assertEquals(74_120L, snapshot.rollup?.pssKb)
        assertTrue(snapshot.rollup!!.fromRollupFile)
        assertEquals(3, snapshot.totalRegions)
        assertEquals("98304 kB", snapshot.status["VmRSS"])
        assertEquals(8_388_608L, snapshot.malloc["arena"])
        assertEquals(137_438_953_472L, snapshot.limits["address_space"])
    }

    @Test
    fun categoriesAreOrderedByTheSpaceTheyHold() {
        val snapshot = parseMemoryMap(
            """
            {"regions":{"total":3,"categories":[{"key":"anon","count":2,"size_kb":1024},
                                                {"key":"native_lib","count":1,"size_kb":8192},
                                                {"key":"stack","count":1,"size_kb":4096}]}}
            """.trimIndent()
        )

        assertEquals(listOf("native_lib", "stack", "anon"), snapshot.categories.map { it.key })
        assertEquals(13_312L, snapshot.mappedKb)
    }

    @Test
    fun reservationsAreCountedApartFromTheCategories() {
        val snapshot = parseMemoryMap(
            """
            {"regions":{"total":10,"reserved_count":3,"reserved_kb":8388608,
                        "categories":[{"key":"anon","count":7,"size_kb":1024}]}}
            """.trimIndent()
        )

        assertEquals(3, snapshot.reservedRegions)
        assertEquals(8_388_608L, snapshot.reservedKb)
        // The share each category is drawn as must be of the accessible total, not of an address
        // space that is four fifths reservation.
        assertEquals(1_024L, snapshot.mappedKb)
    }

    @Test
    fun aMissingRollupIsNullRatherThanAnEmptyReading() {
        val snapshot = parseMemoryMap("""{"regions":{"total":0,"categories":[]}}""")

        assertNull(snapshot.rollup)
        assertEquals(0, snapshot.totalRegions)
        assertTrue(snapshot.categories.isEmpty())
    }

    @Test
    fun theSmapsFallbackIsDistinguishableFromTheKernelSummary() {
        val snapshot = parseMemoryMap("""{"rollup":{"rss_kb":1,"pss_kb":1,"from_rollup_file":false}}""")

        assertFalse(snapshot.rollup!!.fromRollupFile)
    }
}
