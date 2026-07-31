package com.aoscoremonitor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aoscoremonitor.R
import com.aoscoremonitor.diagnostics.Collected
import com.aoscoremonitor.diagnostics.HalInterfaceCollector
import com.aoscoremonitor.ui.components.EmptyState
import com.aoscoremonitor.ui.components.LabeledValue
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.MonitorTab
import com.aoscoremonitor.ui.components.MonitorTabs
import com.aoscoremonitor.ui.components.ReadingStatus
import com.aoscoremonitor.ui.components.SampleDataBanner
import com.aoscoremonitor.ui.components.SectionHeader
import com.aoscoremonitor.ui.components.StatusRow
import com.aoscoremonitor.ui.components.TabContentList
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.HalInfoViewModel
import com.aoscoremonitor.ui.viewmodel.monitorViewModel

@Composable
fun HalInfoScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HalInfoViewModel = monitorViewModel { HalInfoViewModel(it) }
) {
    val halData by viewModel.uiState.collectAsStateWithLifecycle()

    HalInfoContent(
        halData = halData,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
private fun HalInfoContent(halData: HalInterfaceCollector.HalData, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val tabs = listOf(
        MonitorTab(stringResource(R.string.hal_tab_interfaces), Icons.Default.Hardware, halData.halInterfaces.value.size),
        MonitorTab(stringResource(R.string.hal_tab_services), Icons.Default.Devices, halData.hwServices.value.size),
        // No count: the tab holds one property, not a list. It used to be badged with the length of
        // a hard-coded library list, so the badge said "5" on every device.
        MonitorTab(stringResource(R.string.hal_tab_vndk), Icons.Default.Memory)
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    MonitorScaffold(
        title = stringResource(R.string.hal_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            MonitorTabs(tabs = tabs, pagerState = pagerState)
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> HalInterfacesTab(halData.halInterfaces)
                    1 -> HwServicesTab(halData.hwServices)
                    else -> VndkInfoTab(halData.vndkInfo)
                }
            }
        }
    }
}

@Composable
private fun HalInterfacesTab(collected: Collected<List<HalInterfaceCollector.HalInterface>>, modifier: Modifier = Modifier) {
    val interfaces = collected.value

    TabContentList(modifier = modifier) {
        item(key = "header") {
            SectionHeader(
                title = stringResource(R.string.hal_interfaces_section),
                subtitle = stringResource(R.string.hal_interfaces_subtitle),
                icon = Icons.Default.Hardware
            )
        }
        if (collected.isSample) {
            item(key = "sample") { SampleDataBanner(stringResource(R.string.hal_sample_interfaces)) }
        }
        if (interfaces.isEmpty()) {
            item(key = "empty") { EmptyState(stringResource(R.string.hal_interfaces_empty)) }
        } else {
            items(interfaces, key = { "${it.name}-${it.version}" }) { halInterface ->
                MonitorCard {
                    StatusRow(
                        label = halInterface.name,
                        status = halInterface.readingStatus,
                        statusDescription = halInterface.status
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraLarge)
                    ) {
                        LabeledValue(stringResource(R.string.hal_version), halInterface.version)
                        LabeledValue(stringResource(R.string.hal_type), halInterface.type)
                    }
                    LabeledValue(stringResource(R.string.hal_implementation), halInterface.implementation)
                }
            }
        }
    }
}

@Composable
private fun HwServicesTab(collected: Collected<List<HalInterfaceCollector.HwService>>, modifier: Modifier = Modifier) {
    val services = collected.value

    TabContentList(modifier = modifier) {
        item(key = "header") {
            SectionHeader(
                title = stringResource(R.string.hal_services_section),
                subtitle = stringResource(R.string.hal_services_subtitle),
                icon = Icons.Default.Devices
            )
        }
        if (collected.isSample) {
            item(key = "sample") { SampleDataBanner(stringResource(R.string.hal_sample_services)) }
        }
        if (services.isEmpty()) {
            item(key = "empty") { EmptyState(stringResource(R.string.hal_services_empty)) }
        } else {
            items(services, key = { it.name }) { service ->
                MonitorCard {
                    Text(text = service.name, style = MaterialTheme.typography.titleMedium)
                    LabeledValue(
                        label = stringResource(R.string.hal_service_interface),
                        value = service.interfaceDescriptor
                            .ifEmpty { stringResource(R.string.hal_service_no_interface) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VndkInfoTab(vndkInfo: HalInterfaceCollector.VndkInfo, modifier: Modifier = Modifier) {
    TabContentList(modifier = modifier) {
        item(key = "header") {
            SectionHeader(
                title = stringResource(R.string.hal_vndk_section),
                subtitle = stringResource(R.string.hal_vndk_subtitle),
                icon = Icons.Default.Memory
            )
        }
        item(key = "version") {
            MonitorCard {
                val version = vndkInfo.version
                Text(
                    text = if (version != null) {
                        stringResource(R.string.hal_vndk_version, version)
                    } else {
                        stringResource(R.string.hal_vndk_unset)
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.hal_vndk_explainer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // The list of library names that stood here was hard-coded, and named files this app never
        // read. What is genuinely mapped into the process is on the native libraries screen.
        item(key = "libraries-hint") {
            Text(
                text = stringResource(R.string.hal_vndk_libraries_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.Small)
            )
        }
    }
}

/**
 * Whether a HAL is up.
 *
 * [HalInterfaceCollector.HalInterface.status] is a literal the collector writes in English, so the
 * comparison has to be against that literal. It was briefly compared against a string resource,
 * which would have made every HAL read as stopped the moment the app was translated.
 */
private val HalInterfaceCollector.HalInterface.readingStatus: ReadingStatus
    get() = if (status == RUNNING_STATUS) ReadingStatus.Ok else ReadingStatus.Problem

private const val RUNNING_STATUS = "Running"

@Preview(name = "HAL", showBackground = true)
@Preview(name = "HAL (dark)", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HalInfoPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        HalInfoContent(
            halData = HalInterfaceCollector.HalData(
                halInterfaces = Collected.real(
                    listOf(
                        HalInterfaceCollector.HalInterface(
                            name = "android.hardware.audio@7.1::IDevicesFactory",
                            version = "7.1",
                            type = "HIDL",
                            implementation = "default",
                            status = "Running"
                        ),
                        HalInterfaceCollector.HalInterface(
                            name = "android.hardware.camera.provider@2.7::ICameraProvider",
                            version = "2.7",
                            type = "HIDL",
                            implementation = "legacy",
                            status = "Stopped"
                        )
                    )
                ),
                hwServices = Collected.real(emptyList()),
                vndkInfo = HalInterfaceCollector.VndkInfo(version = "36")
            ),
            onNavigateBack = {}
        )
    }
}
