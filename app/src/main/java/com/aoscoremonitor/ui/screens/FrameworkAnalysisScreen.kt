package com.aoscoremonitor.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aoscoremonitor.R
import com.aoscoremonitor.diagnostics.Collected
import com.aoscoremonitor.diagnostics.FrameworkAnalyzer
import com.aoscoremonitor.ui.components.EmptyState
import com.aoscoremonitor.ui.components.LabeledValue
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.MonitorTabs
import com.aoscoremonitor.ui.components.SampleDataBanner
import com.aoscoremonitor.ui.components.SectionHeader
import com.aoscoremonitor.ui.components.TabContentList
import com.aoscoremonitor.ui.components.TabSpec
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.viewmodel.FrameworkAnalysisViewModel
import com.aoscoremonitor.ui.viewmodel.monitorViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FrameworkAnalysisScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FrameworkAnalysisViewModel = monitorViewModel { FrameworkAnalysisViewModel(it) }
) {
    val frameworkData by viewModel.uiState.collectAsStateWithLifecycle()

    FrameworkAnalysisContent(
        frameworkData = frameworkData,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
private fun FrameworkAnalysisContent(
    frameworkData: FrameworkAnalyzer.FrameworkData,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        TabSpec(stringResource(R.string.framework_tab_binder), Icons.Default.Sync, frameworkData.binderTransactions.size),
        TabSpec(stringResource(R.string.framework_tab_api), Icons.Default.Api, frameworkData.apiCalls.value.size),
        TabSpec(
            stringResource(R.string.framework_tab_services),
            Icons.Default.Link,
            frameworkData.serviceData.value.runningServices.size
        )
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    MonitorScaffold(
        title = stringResource(R.string.framework_title),
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
                    0 -> BinderTransactionsTab(frameworkData.binderTransactions)
                    1 -> ApiCallsTab(frameworkData.apiCalls)
                    else -> ServicesTab(frameworkData.serviceData)
                }
            }
        }
    }
}

/**
 * The timestamp format shared by the Binder and API tabs.
 *
 * Held as a [DateTimeFormatter] rather than the `SimpleDateFormat` these tabs each built inline:
 * that one was reconstructed on every recomposition, and being mutable it was never safe to hoist.
 */
@Composable
private fun rememberTimeFormatter(): DateTimeFormatter {
    val locale = LocalLocale.current.platformLocale
    return remember(locale) {
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS", locale).withZone(ZoneId.systemDefault())
    }
}

@Composable
private fun BinderTransactionsTab(transactions: List<FrameworkAnalyzer.BinderTransaction>, modifier: Modifier = Modifier) {
    val timeFormatter = rememberTimeFormatter()

    TabContentList(modifier = modifier) {
        item(key = "header") {
            SectionHeader(
                title = stringResource(R.string.framework_binder_section),
                subtitle = stringResource(R.string.framework_binder_subtitle)
            )
        }
        if (transactions.isEmpty()) {
            item(key = "empty") { EmptyState(stringResource(R.string.framework_binder_empty)) }
        } else {
            items(transactions, key = { "${it.pid}-${it.timestamp}-${it.transactionCode}" }) { transaction ->
                MonitorCard {
                    Text(
                        text = "${transaction.process} (${transaction.pid})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    LabeledValue(
                        label = stringResource(R.string.framework_binder_destination),
                        value = transaction.destination
                    )
                    LabeledValue(
                        label = stringResource(R.string.framework_binder_code),
                        value = "0x${transaction.transactionCode.toString(16)}"
                    )
                    LabeledValue(
                        label = stringResource(R.string.framework_binder_size),
                        value = pluralStringResource(
                            R.plurals.framework_bytes,
                            transaction.dataSize,
                            transaction.dataSize
                        )
                    )
                    Timestamp(timeFormatter.format(Instant.ofEpochMilli(transaction.timestamp)))
                }
            }
        }
    }
}

@Composable
private fun ApiCallsTab(collected: Collected<List<FrameworkAnalyzer.ApiCallInfo>>, modifier: Modifier = Modifier) {
    val timeFormatter = rememberTimeFormatter()
    val apiCalls = collected.value

    TabContentList(modifier = modifier) {
        item(key = "header") {
            SectionHeader(
                title = stringResource(R.string.framework_api_section),
                subtitle = stringResource(R.string.framework_api_subtitle)
            )
        }
        if (collected.isSample) {
            item(key = "sample") { SampleDataBanner(stringResource(R.string.framework_sample_api)) }
        }
        if (apiCalls.isEmpty()) {
            item(key = "empty") { EmptyState(stringResource(R.string.framework_api_empty)) }
        } else {
            items(apiCalls, key = { "${it.apiName}-${it.timestamp}" }) { call ->
                MonitorCard {
                    Text(text = call.apiName, style = MaterialTheme.typography.titleMedium)
                    LabeledValue(
                        label = stringResource(R.string.framework_api_caller),
                        value = call.callerPackage
                    )
                    LabeledValue(
                        label = stringResource(R.string.framework_api_duration),
                        value = stringResource(R.string.framework_millis, call.duration)
                    )
                    Timestamp(timeFormatter.format(Instant.ofEpochMilli(call.timestamp)))
                }
            }
        }
    }
}

@Composable
private fun ServicesTab(collected: Collected<FrameworkAnalyzer.ServiceManagerData>, modifier: Modifier = Modifier) {
    val serviceData = collected.value
    val services = remember(serviceData.runningServices) { serviceData.runningServices.entries.toList() }

    TabContentList(modifier = modifier) {
        item(key = "services-header") {
            SectionHeader(
                title = stringResource(R.string.framework_services_section),
                subtitle = stringResource(R.string.framework_services_subtitle)
            )
        }
        if (collected.isSample) {
            item(key = "sample") { SampleDataBanner(stringResource(R.string.framework_sample_services)) }
        }
        if (services.isEmpty()) {
            item(key = "services-empty") { EmptyState(stringResource(R.string.framework_services_empty)) }
        } else {
            items(services, key = { "service-${it.key}" }) { (service, state) ->
                MonitorCard {
                    Text(text = service, style = MaterialTheme.typography.titleMedium)
                    LabeledValue(label = stringResource(R.string.framework_service_state), value = state)
                }
            }
        }

        item(key = "connections-header") {
            SectionHeader(
                title = stringResource(R.string.framework_connections_section),
                subtitle = stringResource(R.string.framework_connections_subtitle)
            )
        }
        if (serviceData.serviceConnections.isEmpty()) {
            item(key = "connections-empty") { EmptyState(stringResource(R.string.framework_connections_empty)) }
        } else {
            items(serviceData.serviceConnections, key = { "connection-${it.first}-${it.second}" }) { (client, service) ->
                MonitorCard {
                    Text(text = client, style = MaterialTheme.typography.titleMedium)
                    LabeledValue(label = stringResource(R.string.framework_connection_target), value = service)
                }
            }
        }
    }
}

@Composable
private fun Timestamp(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Preview(name = "Framework", showBackground = true)
@Preview(name = "Framework (dark)", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FrameworkAnalysisPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        FrameworkAnalysisContent(
            frameworkData = FrameworkAnalyzer.FrameworkData(
                binderTransactions = listOf(
                    FrameworkAnalyzer.BinderTransaction(
                        process = "system_server",
                        pid = 1731,
                        transactionCode = 26,
                        destination = "android.app.IActivityManager",
                        dataSize = 148,
                        timestamp = 1_754_000_000_000
                    )
                ),
                apiCalls = Collected.real(emptyList()),
                serviceData = Collected.real(
                    FrameworkAnalyzer.ServiceManagerData(
                        runningServices = mapOf("com.android.systemui/.SystemUIService" to "running"),
                        serviceConnections = emptyList()
                    )
                )
            ),
            onNavigateBack = {}
        )
    }
}
