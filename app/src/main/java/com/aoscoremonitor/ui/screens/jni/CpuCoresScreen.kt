package com.aoscoremonitor.ui.screens.jni

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aoscoremonitor.R
import com.aoscoremonitor.diagnostics.jni.CpuCore
import com.aoscoremonitor.diagnostics.jni.CpuSnapshot
import com.aoscoremonitor.diagnostics.jni.Unavailable
import com.aoscoremonitor.ui.components.EmptyState
import com.aoscoremonitor.ui.components.FullScreenMessage
import com.aoscoremonitor.ui.components.LabeledValue
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.SectionHeader
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.MonitorPreviews
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.CpuCoresUiState
import com.aoscoremonitor.ui.viewmodel.CpuCoresViewModel

@Composable
fun CpuCoresScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier, viewModel: CpuCoresViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CpuCoresContent(uiState = uiState, onNavigateBack = onNavigateBack, modifier = modifier)
}

@Composable
private fun CpuCoresContent(uiState: CpuCoresUiState, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    MonitorScaffold(
        title = stringResource(R.string.cpu_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) { innerPadding ->
        val cpu = uiState.cpu
        if (cpu == null) {
            FullScreenMessage(
                message = stringResource(if (uiState.hasLoaded) R.string.cpu_unavailable else R.string.cpu_loading),
                icon = Icons.Default.DeveloperBoard,
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
            item(key = "overview-header") {
                SectionHeader(
                    title = stringResource(R.string.cpu_overview_section),
                    subtitle = stringResource(R.string.cpu_overview_subtitle),
                    icon = Icons.Default.DeveloperBoard
                )
            }
            item(key = "overview") { CpuOverviewCard(cpu) }

            item(key = "cores-header") {
                SectionHeader(
                    title = stringResource(R.string.cpu_cores_section),
                    subtitle = stringResource(R.string.cpu_cores_subtitle),
                    icon = Icons.Default.Speed
                )
            }
            items(cpu.cores, key = { core -> core.id }) { core -> CoreCard(core) }

            item(key = "features-header") {
                SectionHeader(
                    title = stringResource(R.string.cpu_features_section),
                    subtitle = stringResource(R.string.cpu_features_subtitle),
                    icon = Icons.Default.Extension
                )
            }
            item(key = "features") {
                if (cpu.features.isEmpty()) {
                    EmptyState(stringResource(R.string.cpu_features_empty))
                } else {
                    FeatureChips(cpu.features)
                }
            }
        }
    }
}

@Composable
private fun CpuOverviewCard(cpu: CpuSnapshot, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.cpu_core_count, cpu.configuredCores, cpu.onlineCores),
            style = MaterialTheme.typography.titleMedium
        )
        cpu.machine?.let { LabeledValue(label = stringResource(R.string.cpu_machine), value = it) }
        cpu.kernelRelease?.let { LabeledValue(label = stringResource(R.string.cpu_kernel), value = it) }
        LabeledValue(
            label = stringResource(R.string.cpu_page_size),
            value = stringResource(R.string.cpu_bytes, cpu.pageSize)
        )
        LabeledValue(
            label = stringResource(R.string.cpu_clock_ticks),
            value = cpu.clockTicks.toString()
        )
    }
}

/**
 * One core.
 *
 * The frequency is stated on its own line at title weight because it is the number that moves —
 * everything else on the card is there to give it a scale to be read against.
 */
@Composable
private fun CoreCard(core: CpuCore, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.cpu_core_label, core.id),
                style = MaterialTheme.typography.titleMedium
            )
            val badge = when {
                !core.online -> stringResource(R.string.cpu_core_offline)
                core.packageId != null -> stringResource(R.string.cpu_core_cluster, core.packageId)
                else -> null
            }
            badge?.let { Chip(text = it) }
        }

        // A frequency is a number to be read at a glance; the reason it is missing is a sentence.
        // They do not belong at the same weight.
        if (core.curKhz != null) {
            Text(text = frequency(core.curKhz), style = MaterialTheme.typography.titleLarge)
        } else {
            Text(
                text = stringResource(unavailableReason(core.frequencyUnavailable)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        core.frequencyFraction?.let { fraction ->
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.ExtraSmall)
            )
        }

        if (core.minKhz != null && core.maxKhz != null) {
            Text(
                text = if (core.maxKhz >= KHZ_PER_MHZ) {
                    stringResource(R.string.cpu_frequency_range, core.minKhz / KHZ_PER_MHZ, core.maxKhz / KHZ_PER_MHZ)
                } else {
                    stringResource(R.string.cpu_frequency_range_khz, core.minKhz, core.maxKhz)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        core.governor?.let {
            Text(
                text = stringResource(R.string.cpu_governor, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A frequency at whichever scale states it without rounding it away.
 *
 * Megahertz for anything a real core runs at, kilohertz below that. The emulator's cpufreq nodes
 * answer with single-digit kilohertz, which in megahertz is "0 MHz" on every core — a reading that
 * looks like a failure rather than like the stub value it is.
 */
@Composable
private fun frequency(khz: Long): String = if (khz >= KHZ_PER_MHZ) {
    stringResource(R.string.cpu_megahertz, khz / KHZ_PER_MHZ)
} else {
    stringResource(R.string.cpu_kilohertz, khz)
}

private const val KHZ_PER_MHZ = 1000L

/**
 * What to say in place of a frequency.
 *
 * The reason travels up from the errno the read failed with, so the screen can separate a value
 * the sandbox is refused from one this kernel never had. Without a reason it says only that the
 * reading is missing, which is all it used to be able to say.
 */
@StringRes
private fun unavailableReason(reason: Unavailable?): Int = when (reason) {
    Unavailable.Denied -> R.string.cpu_frequency_denied
    Unavailable.Absent -> R.string.cpu_frequency_absent
    else -> R.string.cpu_frequency_unavailable
}

/**
 * The instruction set extensions, as a wrapping run of chips.
 *
 * Plain surfaces rather than Material's `AssistChip`: a chip is a control and takes an `onClick`,
 * and putting forty tappable things on screen that do nothing when tapped is worse for a screen
 * reader than the flat text this actually is.
 */
@Composable
private fun FeatureChips(features: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        features.forEach { feature -> Chip(text = feature) }
    }
}

@Composable
private fun Chip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.ExtraSmall)
        )
    }
}

@MonitorPreviews
@Composable
private fun CpuCoresPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        CpuCoresContent(
            uiState = CpuCoresUiState(
                cpu = CpuSnapshot(
                    configuredCores = 8,
                    onlineCores = 8,
                    pageSize = 4096,
                    clockTicks = 100,
                    machine = "aarch64",
                    kernelRelease = "5.15.123-android14",
                    cores = listOf(
                        CpuCore(id = 0, packageId = 0, minKhz = 300_000, maxKhz = 1_804_800, curKhz = 1_113_600, governor = "schedutil"),
                        CpuCore(id = 1, packageId = 0, minKhz = 300_000, maxKhz = 1_804_800, curKhz = 300_000, governor = "schedutil"),
                        CpuCore(id = 7, packageId = 1, minKhz = 500_000, maxKhz = 2_841_600, online = false)
                    ),
                    features = listOf("fp", "asimd", "aes", "sha2", "crc32", "atomics", "asimddp", "sve")
                ),
                hasLoaded = true
            ),
            onNavigateBack = {}
        )
    }
}
