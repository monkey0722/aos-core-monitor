package com.aoscoremonitor.diagnostics.jni

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
    fun descriptorInspectorListsThisProcessesDescriptors() = runBlocking {
        val snapshot = NativeDescriptorInspector().read()

        assertNotNull("Descriptor table could not be read", snapshot)
        assertTrue("No descriptors reported", snapshot!!.descriptors.isNotEmpty())
        // Every process is handed the three standard streams, whatever else it holds.
        assertTrue("fd 0 is missing, which cannot be", snapshot.descriptors.any { it.fd == 0 })
        assertTrue("No soft limit reported", (snapshot.softLimit ?: 0) > 0)
    }

    /**
     * The walk does not report the handle it walked with.
     *
     * `/proc/self/fd` is read through a descriptor that appears in its own listing, so a collector
     * that reads and reports in one pass claims the process holds a descriptor on the very
     * directory the reading came from. The two-pass design exists to make that unreachable; without
     * this, nothing would notice it coming back.
     */
    @Test
    fun descriptorInspectorDoesNotReportItsOwnDirectoryHandle() = runBlocking {
        val snapshot = NativeDescriptorInspector().read()

        assertNotNull("Descriptor table could not be read", snapshot)
        assertTrue(
            "The descriptor walk reported the directory it was walking",
            snapshot!!.descriptors.none { it.target == "/proc/self/fd" }
        )
    }

    @Test
    fun credentialsInspectorReadsThisProcess() = runBlocking {
        val credentials = NativeCredentialsInspector().read()

        assertNotNull("Credentials could not be read", credentials)
        assertEquals("Reported a different process", Process.myPid(), credentials!!.pid)
        assertEquals("Reported a different user", Process.myUid(), credentials.user?.effective)
        // The kernel prints all five sets on every Android kernel this app runs on; the effective
        // one is the set that decides what a syscall is allowed to do, so its absence is a failure
        // rather than a reading that is allowed to be missing.
        assertNotNull("No effective capability set", credentials.capabilities["effective"])
        assertNotNull("No bounding capability set", credentials.capabilities["bounding"])
        assertTrue("No umask reported", credentials.umask?.isNotEmpty() == true)
    }

    /**
     * The SELinux context is a context, not a stray byte.
     *
     * The kernel terminates what it writes to `/proc/self/attr/current`, and that NUL is not
     * whitespace — left on, it crosses to Kotlin as part of the MLS level and the type would still
     * parse out fine, so only the shape of the whole string catches it.
     */
    @Test
    fun theSelinuxContextArrivesAsAContext() = runBlocking {
        val credentials = NativeCredentialsInspector().read()
        val context = credentials?.selinuxContext ?: return@runBlocking

        assertTrue("Not a user:role:type context: $context", context.split(':').size >= 3)
        assertFalse("The context carries the kernel's NUL terminator", context.contains('\u0000'))
        // The domain itself is not pinned: a test app is untrusted_app on a stock build, but the
        // same library runs in whatever domain the process it is loaded into was assigned.
        assertTrue("No SELinux type in $context", credentials.selinuxType?.isNotEmpty() == true)
    }

    @Test
    fun storageInspectorReadsTheMountTable() = runBlocking {
        val mounts = NativeStorageInspector().read()

        assertNotNull("Mount table could not be read", mounts)
        assertTrue("No mounts reported", mounts!!.isNotEmpty())
        assertTrue("/proc is not mounted, which cannot be", mounts.any { it.target == "/proc" })
    }

    /**
     * Whatever Vulkan answers, it answers coherently.
     *
     * A device with no loader and one whose driver refuses to start are both allowed here — this
     * suite runs on emulators as well as phones — so what is pinned is that the collector never
     * reports devices it did not get, and that a device it did get is described completely.
     */
    @Test
    fun vulkanInspectorDescribesWhateverItFinds() = runBlocking {
        val snapshot = NativeVulkanInspector().read()

        assertNotNull("Vulkan could not be queried at all", snapshot)
        if (!snapshot!!.loaderPresent || !snapshot.instanceCreated) {
            assertTrue(
                "Devices were reported without an instance to enumerate them",
                snapshot.devices.isEmpty()
            )
            return@runBlocking
        }

        assertNotNull("An instance was created without reporting its version", snapshot.instanceVersion)
        snapshot.devices.forEach { device ->
            assertTrue("A device arrived with no name", device.name.isNotEmpty())
            // Every Vulkan implementation has at least one queue family and one memory heap; a
            // device reporting neither means the second enumeration call was skipped.
            assertTrue("${device.name} reported no queue families", device.queueFamilies.isNotEmpty())
            assertTrue("${device.name} reported no memory heaps", device.memoryHeaps.isNotEmpty())
            assertTrue("${device.name} reported no memory", device.totalMemoryBytes > 0)
            assertTrue("${device.name} reported no extensions", device.extensions.isNotEmpty())
        }
    }

    /**
     * The instance is destroyed and the loader survives being reopened.
     *
     * Drivers cap how many live instances a process may hold, so a collector that leaked one would
     * pass once and then fail — which is exactly what the screen's refresh does. It also exercises
     * the loader across readings: a collector that unloaded libvulkan between them would crash here,
     * on a thread the driver owns, rather than in front of a user.
     */
    @Test
    fun vulkanInspectorCanBeReadRepeatedly() = runBlocking {
        val inspector = NativeVulkanInspector()
        val readings = (1..3).map { inspector.read() }

        readings.forEachIndexed { index, snapshot ->
            assertNotNull("Reading ${index + 1} came back empty", snapshot)
        }
        assertEquals(
            "Repeated readings disagreed about how many devices there are",
            1,
            readings.map { it?.devices?.size }.distinct().size
        )
    }

    /**
     * Every probe puts its request and comes back with either an answer or a reason.
     *
     * A device with no audio HAL may refuse all four, and an emulator often refuses the exclusive
     * one, so which of them open is not pinned. What is pinned is that a probe never reports
     * readings it did not take, and never fails silently.
     */
    @Test
    fun audioInspectorPutsEveryRequestToTheSystem() = runBlocking {
        val snapshot = NativeAudioInspector().read()

        assertNotNull("The audio path could not be read", snapshot)
        assertEquals("Not every probe came back", 4, snapshot!!.probes.size)

        snapshot.probes.forEach { probe ->
            if (probe.opened) {
                assertNotNull("${probe.label} opened and reported nothing", probe.granted)
                // The collector writes the hardware object whenever the API-34 guard passes, so a
                // device that can take the reading and an open stream must produce one.
                if (snapshot.hardwareQueryAvailable) {
                    assertNotNull("${probe.label} took no hardware reading", probe.hardware)
                }
            } else {
                assertNotNull("${probe.label} was refused without saying why", probe.openError)
                assertNull("${probe.label} was refused and still reported readings", probe.granted)
            }
        }
    }

    /**
     * The probe streams are closed, not leaked.
     *
     * AAudio caps how many streams a process may hold open, so a collector that forgot to close
     * them would pass once and then start reporting refusals — which is what the screen's refresh
     * would do to it. Four readings is more than the cap allows to be leaked.
     *
     * One probe rather than the count of all four. What the system grants is not device-constant:
     * another app taking the exclusive MMAP path between two readings legitimately turns a granted
     * exclusive stream into a refused one, and a test that counted every probe would call that a
     * leak. The ordinary shared path is the one a device with a working audio HAL always grants, and
     * a leak is exactly what would stop it.
     */
    @Test
    fun audioProbeStreamsAreClosedBetweenReadings() = runBlocking {
        val inspector = NativeAudioInspector()
        val opened = (1..4).map { reading ->
            val snapshot = inspector.read()
            assertNotNull("Reading $reading came back empty", snapshot)
            val probe = snapshot!!.probes.find { it.label == ORDINARY_PROBE }
            assertNotNull("Reading $reading did not put the $ORDINARY_PROBE request at all", probe)
            probe!!.opened
        }

        // Skipped rather than passed on a device that grants nothing: with every reading refused the
        // comparison below holds for a collector that leaks every stream it opens, and a test that
        // reports success for proving nothing is worse than one that says it did not run.
        assumeTrue(
            "This device does not grant the ordinary shared path at all, so a leak would not show here",
            opened.first()
        )
        assertTrue(
            "The ordinary shared stream stopped opening across repeated readings, which is what a leak looks like: $opened",
            opened.all { it }
        )
    }

    private companion object {
        /** The probe that any device with an audio HAL grants, whatever else is playing. */
        const val ORDINARY_PROBE = "default_shared"
    }
}
