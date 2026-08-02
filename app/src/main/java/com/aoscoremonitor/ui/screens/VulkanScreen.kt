package com.aoscoremonitor.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aoscoremonitor.R
import com.aoscoremonitor.diagnostics.formatBytes
import com.aoscoremonitor.diagnostics.jni.VulkanDevice
import com.aoscoremonitor.diagnostics.jni.VulkanDeviceType
import com.aoscoremonitor.diagnostics.jni.VulkanMemoryHeap
import com.aoscoremonitor.diagnostics.jni.VulkanQueueFamily
import com.aoscoremonitor.diagnostics.jni.VulkanSnapshot
import com.aoscoremonitor.ui.components.ExpandableTextCard
import com.aoscoremonitor.ui.components.FullScreenMessage
import com.aoscoremonitor.ui.components.LabeledValue
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.MonitorTag
import com.aoscoremonitor.ui.components.SectionHeader
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.MonitorPreviews
import com.aoscoremonitor.ui.theme.MonitorTypography
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.VulkanUiState
import com.aoscoremonitor.ui.viewmodel.VulkanViewModel

@Composable
fun VulkanScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier, viewModel: VulkanViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    VulkanContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRefresh = viewModel::refresh,
        modifier = modifier
    )
}

@Composable
private fun VulkanContent(uiState: VulkanUiState, onNavigateBack: () -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    MonitorScaffold(
        title = stringResource(R.string.vulkan_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        floatingActionButton = {
            // Nothing here changes while the app runs, so a new reading has to be asked for.
            FloatingActionButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.action_refresh)
                )
            }
        }
    ) { innerPadding ->
        // Checked here rather than folded into unavailableMessage, so that the compiler carries the
        // result: with the null case decided in another function nothing but a `!!` reached the
        // readings below, and an edit that dropped a branch there would have compiled and thrown.
        val snapshot = uiState.vulkan
        if (snapshot == null) {
            FullScreenMessage(
                message = stringResource(
                    if (uiState.hasLoaded) R.string.vulkan_unavailable else R.string.vulkan_loading
                ),
                icon = Icons.Default.ViewInAr,
                modifier = Modifier.padding(innerPadding)
            )
            return@MonitorScaffold
        }

        val message = unavailableMessage(snapshot)
        if (message != null) {
            FullScreenMessage(
                message = message,
                icon = Icons.Default.ViewInAr,
                modifier = Modifier.padding(innerPadding)
            )
            return@MonitorScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            item(key = "summary-header") {
                SectionHeader(
                    title = stringResource(R.string.vulkan_summary_section),
                    subtitle = pluralStringResource(
                        R.plurals.vulkan_summary,
                        snapshot.devices.size,
                        snapshot.devices.size,
                        snapshot.instanceVersion.orEmpty()
                    ),
                    icon = Icons.Default.ViewInAr
                )
            }

            snapshot.devices.forEachIndexed { index, device ->
                item(key = "device-$index") { DeviceCard(device) }
                item(key = "memory-$index") { MemoryCard(device) }
                item(key = "queues-$index") { QueueCard(device) }
                item(key = "limits-$index") { LimitsCard(device) }
                if (device.extensions.isNotEmpty()) {
                    item(key = "extensions-$index") {
                        ExpandableTextCard(
                            title = stringResource(R.string.vulkan_extensions_section),
                            text = device.extensions.joinToString(separator = "\n"),
                            icon = Icons.Default.ViewInAr,
                            supportingText = stringResource(
                                R.string.vulkan_extensions_summary,
                                device.extensions.size
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * The wording for a reading that found no devices, or null when it found some.
 *
 * Three ways of having none, which are three different statements: the device has no Vulkan loader
 * at all, the loader is there with no driver behind it, and a driver that started and enumerated
 * nothing. Collapsing them into one "unavailable" would throw away the only part of the reading
 * that survived.
 *
 * A snapshot that was never taken is the caller's case, not this one — it is the only branch that
 * decides whether the rest of the screen can run, so it stays where the compiler can see it.
 */
@Composable
private fun unavailableMessage(snapshot: VulkanSnapshot): String? = when {
    !snapshot.loaderPresent -> stringResource(R.string.vulkan_no_loader)
    !snapshot.instanceCreated && snapshot.instanceError != null ->
        stringResource(R.string.vulkan_instance_failed, snapshot.instanceError)
    !snapshot.instanceCreated ->
        stringResource(R.string.vulkan_instance_failed_code, snapshot.instanceErrorCode ?: 0)
    snapshot.devices.isEmpty() -> stringResource(R.string.vulkan_no_devices)
    else -> null
}

@Composable
private fun DeviceCard(device: VulkanDevice, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // Weighted, so that a long name is ellipsised rather than measured at its full
                // width: an unweighted child takes the whole row and pushes the tag off the edge,
                // which is what "Goldfish GFXStream (llvmpipe (LLVM 21.1.4, 128 bits))" did.
                modifier = Modifier
                    .weight(1f)
                    .padding(end = Spacing.Small)
            )
            MonitorTag(stringResource(device.type.labelRes))
        }

        LabeledValue(
            label = stringResource(R.string.vulkan_device_api),
            value = device.apiVersion,
            valueStyle = MonitorTypography.machineText
        )

        if (device.driverName != null) {
            LabeledValue(
                label = stringResource(R.string.vulkan_device_driver),
                value = device.driverName
            )
        }
        if (device.driverInfo != null) {
            Text(
                text = device.driverInfo,
                style = MonitorTypography.machineText,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LabeledValue(
            label = stringResource(R.string.vulkan_device_driver_version),
            // The packed split beside the number it came from: how a vendor encodes its driver
            // version is the vendor's business, so only the raw value is certainly right.
            value = stringResource(
                R.string.vulkan_device_driver_version_value,
                device.driverVersion,
                device.driverVersionRaw
            ),
            valueStyle = MonitorTypography.machineText
        )

        LabeledValue(
            label = stringResource(R.string.vulkan_device_ids),
            value = stringResource(R.string.vulkan_device_ids_value, device.vendorId, device.deviceId),
            valueStyle = MonitorTypography.machineText
        )

        if (device.conformanceVersion != null) {
            LabeledValue(
                label = stringResource(R.string.vulkan_device_conformance),
                value = device.conformanceVersion,
                valueStyle = MonitorTypography.machineText
            )
        }

        if (device.pipelineCacheUuid != null) {
            LabeledValue(
                label = stringResource(R.string.vulkan_device_cache_uuid),
                value = device.pipelineCacheUuid,
                valueStyle = MonitorTypography.machineText
            )
        }

        // Said rather than left blank: the driver identity query reached core in Vulkan 1.2, so a
        // 1.1 device is silent here for a reason the screen can name.
        if (device.driverName == null) {
            Text(
                text = stringResource(R.string.vulkan_device_no_driver_properties),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MemoryCard(device: VulkanDevice, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.vulkan_memory_section),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = pluralStringResource(
                R.plurals.vulkan_memory_summary,
                device.memoryHeaps.size,
                device.memoryHeaps.size,
                formatBytes(device.totalMemoryBytes)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        device.memoryHeaps.forEach { heap -> HeapRow(heap) }
        Text(
            text = pluralStringResource(
                R.plurals.vulkan_memory_types,
                device.memoryTypes.size,
                device.memoryTypes.size
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HeapRow(heap: VulkanMemoryHeap, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.vulkan_heap, heap.index),
            style = MonitorTypography.machineText
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            if (heap.isDeviceLocal) MonitorTag(stringResource(R.string.vulkan_heap_device_local))
            Text(
                text = formatBytes(heap.sizeBytes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QueueCard(device: VulkanDevice, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.vulkan_queues_section),
            style = MaterialTheme.typography.titleSmall
        )
        device.queueFamilies.forEach { family -> QueueFamilyRow(family) }
    }
}

@Composable
private fun QueueFamilyRow(family: VulkanQueueFamily, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.ExtraSmall),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.vulkan_queue_family, family.index),
            style = MonitorTypography.machineText
        )
        Text(
            text = pluralStringResource(R.plurals.vulkan_queue_count, family.queueCount, family.queueCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        if (family.graphics) MonitorTag(stringResource(R.string.vulkan_queue_graphics))
        if (family.compute) MonitorTag(stringResource(R.string.vulkan_queue_compute))
        if (family.transfer) MonitorTag(stringResource(R.string.vulkan_queue_transfer))
        if (family.sparseBinding) MonitorTag(stringResource(R.string.vulkan_queue_sparse))
        if (family.protectedMemory) MonitorTag(stringResource(R.string.vulkan_queue_protected))
    }
}

@Composable
private fun LimitsCard(device: VulkanDevice, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.vulkan_limits_section),
            style = MaterialTheme.typography.titleSmall
        )
        // Named here rather than iterated out of the map: the order the native side wrote them in
        // is not part of the contract, and a raw key is not a label.
        ShownLimits.forEach { limit ->
            val value = device.limits[limit.key] ?: return@forEach
            LabeledValue(
                label = stringResource(limit.labelRes),
                value = if (limit.isBytes) formatBytes(value) else value.toString(),
                valueStyle = MonitorTypography.machineText
            )
        }
    }
}

/** One limit the screen shows, and whether its number is a byte count or a plain one. */
private data class ShownLimit(val key: String, @param:StringRes val labelRes: Int, val isBytes: Boolean = false)

private val ShownLimits = listOf(
    ShownLimit("max_image_dimension_2d", R.string.vulkan_limit_image_2d),
    ShownLimit("max_bound_descriptor_sets", R.string.vulkan_limit_descriptor_sets),
    ShownLimit("max_push_constants_size", R.string.vulkan_limit_push_constants, isBytes = true),
    ShownLimit("max_memory_allocation_count", R.string.vulkan_limit_allocations),
    ShownLimit("max_compute_shared_memory_size", R.string.vulkan_limit_compute_shared, isBytes = true),
    ShownLimit("max_compute_work_group_invocations", R.string.vulkan_limit_compute_invocations)
)

@get:StringRes
private val VulkanDeviceType.labelRes: Int
    get() = when (this) {
        VulkanDeviceType.IntegratedGpu -> R.string.vulkan_type_integrated
        VulkanDeviceType.DiscreteGpu -> R.string.vulkan_type_discrete
        VulkanDeviceType.VirtualGpu -> R.string.vulkan_type_virtual
        VulkanDeviceType.Cpu -> R.string.vulkan_type_cpu
        VulkanDeviceType.Other -> R.string.vulkan_type_other
        VulkanDeviceType.Unknown -> R.string.vulkan_type_unknown
    }

@MonitorPreviews
@Composable
private fun VulkanPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        VulkanContent(
            uiState = VulkanUiState(
                vulkan = VulkanSnapshot(
                    loaderPresent = true,
                    instanceVersion = "1.3.275",
                    instanceCreated = true,
                    devices = listOf(
                        VulkanDevice(
                            name = "Adreno (TM) 740",
                            type = VulkanDeviceType.IntegratedGpu,
                            apiVersion = "1.3.275",
                            driverVersion = "512.780.0",
                            driverVersionRaw = 2_150_573_056,
                            vendorId = "0x5143",
                            deviceId = "0x43050a01",
                            pipelineCacheUuid = "1d3f5e0a9c4b2761d8e0f3a4b5c60718",
                            driverId = 8,
                            driverIdName = "qualcomm_proprietary",
                            driverName = "Qualcomm Technologies Inc. Adreno Vulkan Driver",
                            driverInfo = "Vulkan 1.3.275 (Adreno 740)",
                            conformanceVersion = "1.3.6.3",
                            limits = mapOf(
                                "max_image_dimension_2d" to 16_384,
                                "max_bound_descriptor_sets" to 4,
                                "max_push_constants_size" to 128,
                                "max_memory_allocation_count" to 4_294_967_295,
                                "max_compute_shared_memory_size" to 32_768,
                                "max_compute_work_group_invocations" to 1_024
                            ),
                            memoryHeaps = listOf(
                                VulkanMemoryHeap(index = 0, sizeBytes = 11_453_246_976, isDeviceLocal = true)
                            ),
                            queueFamilies = listOf(
                                VulkanQueueFamily(
                                    index = 0,
                                    queueCount = 3,
                                    graphics = true,
                                    compute = true,
                                    transfer = true,
                                    timestampBits = 48
                                )
                            ),
                            extensions = listOf("VK_KHR_16bit_storage", "VK_KHR_swapchain")
                        )
                    )
                ),
                hasLoaded = true
            ),
            onNavigateBack = {},
            onRefresh = {}
        )
    }
}
