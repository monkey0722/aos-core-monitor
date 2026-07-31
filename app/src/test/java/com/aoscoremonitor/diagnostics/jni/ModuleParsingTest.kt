package com.aoscoremonitor.diagnostics.jni

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleParsingTest {

    @Test
    fun readsModulesLargestFirst() {
        val snapshot = parseLoadedModules(
            """
            {"adds":231,"subs":3,"modules":[
              {"path":"/apex/com.android.runtime/lib64/bionic/libc.so","base":"0x7b0f440000",
               "mapped_size":1052672,"segment_count":4,"relro":true,"tls":true,"build_id":"1ab3ff90"},
              {"path":"/apex/com.android.art/lib64/libart.so","base":"0x7b1c2a0000",
               "mapped_size":8912896,"segment_count":5,"relro":true,"tls":false}]}
            """.trimIndent()
        )

        assertEquals(listOf("libart.so", "libc.so"), snapshot.modules.map { it.fileName })
        assertEquals(231, snapshot.loadEvents)
        assertEquals(3, snapshot.unloadEvents)
        assertEquals(9_965_568L, snapshot.totalMappedSize)
        assertEquals(5, snapshot.modules.first().segmentCount)
    }

    @Test
    fun splitsAPathIntoItsNameAndDirectory() {
        val snapshot = parseLoadedModules(
            """{"modules":[{"path":"/apex/com.android.art/lib64/libart.so","base":"0x1","mapped_size":1}]}"""
        )

        val module = snapshot.modules.single()
        assertEquals("libart.so", module.fileName)
        assertEquals("/apex/com.android.art/lib64", module.directory)
        assertEquals(false, module.isMainExecutable)
    }

    @Test
    fun theMainExecutableHasNoPath() {
        val snapshot = parseLoadedModules("""{"modules":[{"path":"","base":"0x0","mapped_size":4096}]}""")

        val module = snapshot.modules.single()
        assertTrue(module.isMainExecutable)
        assertEquals("", module.directory)
    }

    @Test
    fun anObjectWithoutABuildIdReportsNoneRatherThanAnEmptyString() {
        val snapshot = parseLoadedModules("""{"modules":[{"path":"/x/y.so","base":"0x1","mapped_size":1}]}""")

        assertNull(snapshot.modules.single().buildId)
    }

    @Test
    fun linkerEventCountsAreOptional() {
        val snapshot = parseLoadedModules("""{"modules":[]}""")

        assertNull(snapshot.loadEvents)
        assertNull(snapshot.unloadEvents)
    }
}
