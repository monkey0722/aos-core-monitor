package com.aoscoremonitor.ui.screens.jni

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aoscoremonitor.R
import com.aoscoremonitor.ui.components.EmptyState
import com.aoscoremonitor.ui.components.LabeledValue
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.SectionHeader
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.MonitorPreviews
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.KernelCountersUiState
import com.aoscoremonitor.ui.viewmodel.KernelCountersViewModel

@Composable
fun KernelCountersScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier, viewModel: KernelCountersViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    KernelCountersContent(uiState = uiState, onNavigateBack = onNavigateBack, modifier = modifier)
}

@Composable
private fun KernelCountersContent(uiState: KernelCountersUiState, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    // Resolved before the list is built: a LazyListScope lambda is not a composable context, so
    // stringResource cannot be called inside it.
    val sections = listOf(
        CounterSection(
            key = "cpu",
            title = stringResource(R.string.kernel_cpu_section),
            subtitle = stringResource(R.string.kernel_cpu_subtitle),
            icon = Icons.Default.Speed,
            readings = uiState.cpu.mapValues { (_, value) -> value.toString() },
            emptyMessage = stringResource(R.string.kernel_cpu_unavailable)
        ),
        CounterSection(
            key = "memory",
            title = stringResource(R.string.kernel_memory_section),
            subtitle = stringResource(R.string.kernel_memory_subtitle),
            icon = Icons.Default.Memory,
            readings = uiState.memory.mapValues { (_, value) -> stringResource(R.string.kernel_kilobytes, value) },
            emptyMessage = stringResource(R.string.kernel_memory_unavailable)
        )
    )

    MonitorScaffold(
        title = stringResource(R.string.kernel_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            // These counters are the raw form of what System Info summarises, so the screen says
            // where its readings sit relative to the other two that touch the same files.
            item(key = "subtitle") {
                Text(
                    text = stringResource(R.string.kernel_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.Small)
                )
            }
            // Each group was a bold Text followed by a run of "key: value" strings, which left no
            // boundary between one group's last reading and the next group's heading.
            sections.forEach { section -> counterSection(section) }
        }
    }
}

/** One group of counters, with everything already resolved from resources. */
private data class CounterSection(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val readings: Map<String, String>,
    val emptyMessage: String
)

private fun LazyListScope.counterSection(section: CounterSection) {
    item(key = "${section.key}-header") {
        SectionHeader(title = section.title, subtitle = section.subtitle, icon = section.icon)
    }
    if (section.readings.isEmpty()) {
        item(key = "${section.key}-empty") { EmptyState(section.emptyMessage) }
    } else {
        item(key = "${section.key}-values") {
            MonitorCard {
                section.readings.forEach { (name, value) ->
                    LabeledValue(label = name, value = value)
                }
            }
        }
    }
}

@MonitorPreviews
@Composable
private fun KernelCountersPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        KernelCountersContent(
            uiState = KernelCountersUiState(
                cpu = emptyMap(),
                memory = mapOf("MemTotal" to 2_027_136L, "MemAvailable" to 1_204_992L)
            ),
            onNavigateBack = {}
        )
    }
}
