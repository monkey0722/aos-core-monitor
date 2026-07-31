package com.aoscoremonitor.diagnostics.jni

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks that each native collector is wired to its JNI entry point and reads real data.
 *
 * The parsing tests cover the JSON contract off-device, but they cannot see a misspelled
 * `Java_…` symbol: that surfaces as an [UnsatisfiedLinkError] the first time the screen opens, on
 * a device, with the failure looking like an empty screen. Running the collectors for real is the
 * only thing that catches it.
 *
 * The assertions stay to what holds on any Android device — a process always has an address space,
 * a linker and a mount table — so that a device with a restrictive sysfs does not fail the suite
 * for readings that are allowed to be missing.
 */
@RunWith(AndroidJUnit4::class)
class NativeInspectorTest {

    @Test
    fun cpuInspectorReadsTopology() = runBlocking {
        val snapshot = NativeCpuInspector().readStatic()

        assertNotNull("CPU topology could not be read", snapshot)
        assertTrue("No cores reported", snapshot!!.cores.isNotEmpty())
        assertTrue("No configured core count", snapshot.configuredCores >= 1)
        assertTrue("Page size looks wrong: ${snapshot.pageSize}", snapshot.pageSize >= 4096)
    }

    @Test
    fun cpuInspectorReadsFrequencies() = runBlocking {
        val cores = NativeCpuInspector().readFrequencies()

        // The frequency itself may be denied by SELinux, but the core list is built from sysfs
        // directory names, so it cannot be empty on a running device.
        assertTrue("No cores reported", cores.isNotEmpty())
    }

    /**
     * A reading that is missing says why it is missing.
     *
     * Vacuous on a device that lets the app read every core's frequency, which this emulator does.
     * It is the phones that deny `scaling_cur_freq` — the reason the field is nullable at all —
     * where this has something to check.
     */
    @Test
    fun aMissingFrequencyCarriesItsReason() = runBlocking {
        val cores = NativeCpuInspector().readFrequencies()

        cores.filter { it.curKhz == null }.forEach { core ->
            assertNotNull("core ${core.id} reports no frequency and no reason", core.frequencyUnavailable)
        }
    }

    @Test
    fun memoryInspectorReadsThisProcess() = runBlocking {
        val snapshot = NativeMemoryInspector().read()

        assertNotNull("Memory map could not be read", snapshot)
        assertTrue("No mappings found", snapshot!!.totalRegions > 0)
        assertNotNull("/proc/self smaps reported nothing", snapshot.rollup)
        assertTrue("Resident size was zero", snapshot.rollup!!.rssKb > 0)
        assertTrue("mallinfo2 reported nothing in use", (snapshot.malloc["in_use"] ?: 0) > 0)
    }

    @Test
    fun moduleInspectorListsBionic() = runBlocking {
        val snapshot = NativeModuleInspector().read()

        assertNotNull("Module list could not be read", snapshot)
        val names = snapshot!!.modules.map { it.fileName }
        assertTrue("libc.so is not loaded, which cannot be: $names", names.contains("libc.so"))
        assertTrue(
            "The library under test is not in its own module list",
            names.contains("libsystem_monitor.so")
        )
        assertTrue("No mapped size reported", snapshot.totalMappedSize > 0)
    }

    @Test
    fun threadInspectorListsThisProcessesThreads() = runBlocking {
        val snapshot = NativeThreadInspector().read()

        assertNotNull("Thread list could not be read", snapshot)
        assertTrue("No threads reported", snapshot!!.threads.isNotEmpty())
        // The thread running the test is in its own list, and every Android process is scheduled
        // with a USER_HZ the kernel reports.
        assertTrue("No clock rate reported", snapshot.clockTicks > 0)
        assertTrue(
            "No thread reports a scheduling policy",
            snapshot.threads.any { it.policy != SchedulerPolicy.Unknown }
        )
    }

    @Test
    fun storageInspectorReadsTheMountTable() = runBlocking {
        val mounts = NativeStorageInspector().read()

        assertNotNull("Mount table could not be read", mounts)
        assertTrue("No mounts reported", mounts!!.isNotEmpty())
        assertTrue("/proc is not mounted, which cannot be", mounts.any { it.target == "/proc" })
    }
}
