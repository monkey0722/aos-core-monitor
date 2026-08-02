package com.aoscoremonitor.diagnostics.jni

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VulkanParsingTest {

    @Test
    fun readsADeviceAndItsDriver() {
        val snapshot = parseVulkan(
            """
            {"loader_present":true,"instance_version":"1.3.275","instance_ok":true,"devices":[
              {"name":"Adreno (TM) 740","type":"integrated_gpu","api_version":"1.3.275",
               "vendor_id":"0x5143","device_id":"0x43050a01",
               "driver_version_raw":2150573056,"driver_version":"512.780.0",
               "pipeline_cache_uuid":"1d3f5e0a9c4b2761",
               "driver_id":8,"driver_id_name":"qualcomm_proprietary",
               "driver_name":"Qualcomm Adreno Vulkan Driver","driver_info":"Vulkan 1.3.275",
               "conformance_version":"1.3.6.3"}]}
            """.trimIndent()
        )

        assertTrue(snapshot.loaderPresent)
        assertTrue(snapshot.instanceCreated)
        assertEquals("1.3.275", snapshot.instanceVersion)

        val device = snapshot.devices.single()
        assertEquals("Adreno (TM) 740", device.name)
        assertEquals(VulkanDeviceType.IntegratedGpu, device.type)
        assertEquals("qualcomm_proprietary", device.driverIdName)
        assertEquals(8, device.driverId)
        assertEquals("1.3.6.3", device.conformanceVersion)
        // The raw number survives the trip: it is the only part of the driver version that is
        // certainly right, since the packing is the vendor's own.
        assertEquals(2_150_573_056L, device.driverVersionRaw)
        assertEquals("512.780.0", device.driverVersion)
    }

    /** A 1.1 device answers the properties query and says nothing about its driver. */
    @Test
    fun aDeviceWithoutDriverPropertiesReportsNoneRatherThanAnEmptyString() {
        val snapshot = parseVulkan(
            """
            {"loader_present":true,"instance_ok":true,"devices":[
              {"name":"Mali-G78","type":"integrated_gpu","api_version":"1.1.128",
               "vendor_id":"0x13b5","device_id":"0x70930000",
               "driver_version_raw":1,"driver_version":"0.0.1"}]}
            """.trimIndent()
        )

        val device = snapshot.devices.single()
        assertNull(device.driverIdName)
        assertNull(device.driverId)
        assertNull(device.driverName)
        assertNull(device.conformanceVersion)
    }

    @Test
    fun readsHeapsTypesAndQueueFamilies() {
        val snapshot = parseVulkan(
            """
            {"loader_present":true,"instance_ok":true,"devices":[
              {"name":"g","type":"integrated_gpu","api_version":"1.3.0","vendor_id":"0x1",
               "device_id":"0x2","driver_version_raw":1,"driver_version":"0.0.1",
               "memory_heaps":[{"index":0,"size_bytes":1024,"device_local":true},
                               {"index":1,"size_bytes":512,"device_local":false}],
               "memory_types":[{"index":0,"heap":0,"device_local":true,"host_visible":false}],
               "queue_families":[{"index":0,"count":3,"graphics":true,"compute":true,
                                  "transfer":true,"timestamp_bits":48}]}]}
            """.trimIndent()
        )

        val device = snapshot.devices.single()
        assertEquals(1536L, device.totalMemoryBytes)
        assertEquals(listOf(true, false), device.memoryHeaps.map { it.isDeviceLocal })
        assertEquals(1, device.memoryTypes.size)
        assertFalse(device.memoryTypes.single().isHostVisible)

        val family = device.queueFamilies.single()
        assertEquals(3, family.queueCount)
        assertEquals(48, family.timestampBits)
        assertTrue(family.graphics && family.compute && family.transfer)
        assertFalse(family.sparseBinding)
    }

    @Test
    fun extensionsArriveSorted() {
        val snapshot = parseVulkan(
            """
            {"loader_present":true,"instance_ok":true,"devices":[
              {"name":"g","type":"cpu","api_version":"1.0.0","vendor_id":"0x1","device_id":"0x2",
               "driver_version_raw":1,"driver_version":"0.0.1",
               "extensions":["VK_KHR_swapchain","VK_EXT_debug_marker","VK_KHR_16bit_storage"]}]}
            """.trimIndent()
        )

        assertEquals(
            listOf("VK_EXT_debug_marker", "VK_KHR_16bit_storage", "VK_KHR_swapchain"),
            snapshot.devices.single().extensions
        )
    }

    /**
     * The three ways of having no devices stay apart.
     *
     * A device with no loader, a loader with no driver behind it and a driver that enumerated
     * nothing are different statements, and the screen words each of them differently. A parser
     * that flattened them into an empty list would leave it nothing to say.
     */
    @Test
    fun aDeviceWithNoLoaderSaysSoRatherThanReportingNoDevices() {
        val snapshot = parseVulkan("""{"loader_present":false}""")

        assertFalse(snapshot.loaderPresent)
        assertFalse(snapshot.instanceCreated)
        assertTrue(snapshot.devices.isEmpty())
        assertNull(snapshot.instanceVersion)
    }

    @Test
    fun aFailedInstanceCarriesTheResultThatStoppedIt() {
        val snapshot = parseVulkan(
            """{"loader_present":true,"instance_version":"1.1.0","instance_ok":false,
                "instance_error":"incompatible_driver"}"""
        )

        assertTrue(snapshot.loaderPresent)
        assertFalse(snapshot.instanceCreated)
        assertEquals("incompatible_driver", snapshot.instanceError)
        assertNull(snapshot.instanceErrorCode)
    }

    /** A result this app has no name for crosses as its number rather than as a wrong name. */
    @Test
    fun anUnnamedResultCarriesItsCode() {
        val snapshot = parseVulkan(
            """{"loader_present":true,"instance_ok":false,"instance_error_code":-13}"""
        )

        assertNull(snapshot.instanceError)
        assertEquals(-13, snapshot.instanceErrorCode)
    }

    @Test
    fun anInstanceThatEnumeratedNothingIsNotAFailure() {
        val snapshot = parseVulkan("""{"loader_present":true,"instance_ok":true,"devices":[]}""")

        assertTrue(snapshot.instanceCreated)
        assertTrue(snapshot.devices.isEmpty())
    }

    @Test
    fun anUnnamedDeviceTypeIsUnknownRatherThanAGuess() {
        val snapshot = parseVulkan(
            """
            {"loader_present":true,"instance_ok":true,"devices":[
              {"name":"g","type":"quantum_gpu","api_version":"1.3.0","vendor_id":"0x1",
               "device_id":"0x2","driver_version_raw":1,"driver_version":"0.0.1"}]}
            """.trimIndent()
        )

        assertEquals(VulkanDeviceType.Unknown, snapshot.devices.single().type)
    }
}
