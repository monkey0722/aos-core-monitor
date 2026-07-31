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
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aoscoremonitor.R
import com.aoscoremonitor.ui.components.EmptyState
import com.aoscoremonitor.ui.components.LabeledValue
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.SectionHeader
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.NativeSystemMonitorViewModel
import com.aoscoremonitor.ui.viewmodel.NativeSystemUiState

@Composable
fun NativeSystemMonitorScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NativeSystemMonitorViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    NativeSystemMonitorContent(state = state, onNavigateBack = onNavigateBack, modifier = modifier)
}

@Composable
private fun NativeSystemMonitorContent(state: NativeSystemUiState, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    // Resolved before the list is built: a LazyListScope lambda is not a composable context, so
    // stringResource cannot be called inside it.
    val sections = listOf(
        CounterSection(
            key = "cpu",
            title = stringResource(R.string.native_cpu_section),
            subtitle = stringResource(R.string.native_cpu_subtitle),
            icon = Icons.Default.Speed,
            readings = state.cpu.mapValues { (_, value) -> value.toString() },
            emptyMessage = stringResource(R.string.native_cpu_unavailable)
        ),
        CounterSection(
            key = "memory",
            title = stringResource(R.string.native_memory_section),
            subtitle = stringResource(R.string.native_memory_subtitle),
            icon = Icons.Default.Memory,
            readings = state.memory.mapValues { (_, value) -> stringResource(R.string.native_kilobytes, value) },
            emptyMessage = stringResource(R.string.native_memory_unavailable)
        ),
        CounterSection(
            key = "process",
            title = stringResource(R.string.native_process_section),
            subtitle = stringResource(R.string.native_process_subtitle),
            icon = Icons.Default.Widgets,
            readings = state.process,
            emptyMessage = stringResource(R.string.native_process_unavailable)
        )
    )

    MonitorScaffold(
        title = stringResource(R.string.native_title),
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

@Preview(name = "Native monitor", showBackground = true)
@Preview(name = "Native monitor (dark)", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NativeSystemMonitorPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        NativeSystemMonitorContent(
            state = NativeSystemUiState(
                cpu = emptyMap(),
                memory = mapOf("MemTotal" to 2_027_136L, "MemAvailable" to 1_204_992L),
                process = mapOf("Name" to "com.aoscoremonitor", "VmRSS" to "94112 kB")
            ),
            onNavigateBack = {}
        )
    }
}
