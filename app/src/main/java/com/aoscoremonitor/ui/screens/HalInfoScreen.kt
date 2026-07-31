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
import com.aoscoremonitor.diagnostics.HalInterfaceAnalyzer
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
import com.aoscoremonitor.ui.theme.MonitorTypography
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
private fun HalInfoContent(halData: HalInterfaceAnalyzer.HalData, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val tabs = listOf(
        MonitorTab(stringResource(R.string.hal_tab_interfaces), Icons.Default.Hardware, halData.halInterfaces.value.size),
        MonitorTab(stringResource(R.string.hal_tab_services), Icons.Default.Devices, halData.hwservices.value.size),
        MonitorTab(stringResource(R.string.hal_tab_vndk), Icons.Default.Memory, halData.vndkInfo.libraries.size)
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
                    1 -> HwServicesTab(halData.hwservices)
                    else -> VndkInfoTab(halData.vndkInfo)
                }
            }
        }
    }
}

@Composable
private fun HalInterfacesTab(collected: Collected<List<HalInterfaceAnalyzer.HalInterface>>, modifier: Modifier = Modifier) {
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
private fun HwServicesTab(collected: Collected<List<HalInterfaceAnalyzer.HwService>>, modifier: Modifier = Modifier) {
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
                    LabeledValue(stringResource(R.string.hal_service_server), service.server)
                    LabeledValue(
                        label = stringResource(R.string.hal_service_clients),
                        value = service.clients.joinToString("\n")
                            .ifEmpty { stringResource(R.string.hal_service_no_clients) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VndkInfoTab(vndkInfo: HalInterfaceAnalyzer.VndkInfo, modifier: Modifier = Modifier) {
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
                Text(
                    text = stringResource(R.string.hal_vndk_version, vndkInfo.version),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.hal_vndk_explainer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "libraries-header") {
            SectionHeader(title = stringResource(R.string.hal_vndk_libraries))
        }
        if (vndkInfo.libraries.isEmpty()) {
            item(key = "empty") { EmptyState(stringResource(R.string.hal_vndk_empty)) }
        } else {
            // A library name is one line of text; a card each turned the list into a wall of
            // rounded rectangles, so they are plain rows now.
            items(vndkInfo.libraries, key = { it }) { library ->
                Text(
                    text = library,
                    style = MonitorTypography.machineText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.ExtraSmall)
                )
            }
        }
    }
}

/**
 * Whether a HAL is up.
 *
 * [HalInterfaceAnalyzer.HalInterface.status] is a literal the analyzer writes in English, so the
 * comparison has to be against that literal. It was briefly compared against a string resource,
 * which would have made every HAL read as stopped the moment the app was translated.
 */
private val HalInterfaceAnalyzer.HalInterface.readingStatus: ReadingStatus
    get() = if (status == RUNNING_STATUS) ReadingStatus.Ok else ReadingStatus.Problem

private const val RUNNING_STATUS = "Running"

@Preview(name = "HAL", showBackground = true)
@Preview(name = "HAL (dark)", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HalInfoPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        HalInfoContent(
            halData = HalInterfaceAnalyzer.HalData(
                halInterfaces = Collected.real(
                    listOf(
                        HalInterfaceAnalyzer.HalInterface(
                            name = "android.hardware.audio@7.1::IDevicesFactory",
                            version = "7.1",
                            type = "HIDL",
                            implementation = "default",
                            status = "Running"
                        ),
                        HalInterfaceAnalyzer.HalInterface(
                            name = "android.hardware.camera.provider@2.7::ICameraProvider",
                            version = "2.7",
                            type = "HIDL",
                            implementation = "legacy",
                            status = "Stopped"
                        )
                    )
                ),
                hwservices = Collected.real(emptyList()),
                vndkInfo = HalInterfaceAnalyzer.VndkInfo(version = "36", libraries = listOf("libbase.so", "libcutils.so"))
            ),
            onNavigateBack = {}
        )
    }
}
