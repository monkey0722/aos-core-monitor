package com.aoscoremonitor.ui.screens.jni

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aoscoremonitor.R
import com.aoscoremonitor.diagnostics.formatBytes
import com.aoscoremonitor.diagnostics.formatKilobytes
import com.aoscoremonitor.diagnostics.jni.MemoryRollup
import com.aoscoremonitor.diagnostics.jni.MemorySnapshot
import com.aoscoremonitor.diagnostics.jni.RegionCategory
import com.aoscoremonitor.ui.components.FullScreenMessage
import com.aoscoremonitor.ui.components.LabeledValue
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.SectionHeader
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.MemoryMapUiState
import com.aoscoremonitor.ui.viewmodel.MemoryMapViewModel

@Composable
fun MemoryMapScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier, viewModel: MemoryMapViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MemoryMapContent(uiState = uiState, onNavigateBack = onNavigateBack, modifier = modifier)
}

@Composable
private fun MemoryMapContent(uiState: MemoryMapUiState, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    MonitorScaffold(
        title = stringResource(R.string.memory_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) { innerPadding ->
        val memory = uiState.memory
        if (memory == null) {
            FullScreenMessage(
                message = stringResource(if (uiState.hasLoaded) R.string.memory_unavailable else R.string.memory_loading),
                icon = Icons.Default.DataUsage,
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
            memory.rollup?.let { rollup ->
                item(key = "footprint-header") {
                    SectionHeader(
                        title = stringResource(R.string.memory_footprint_section),
                        subtitle = stringResource(R.string.memory_footprint_subtitle),
                        icon = Icons.Default.DataUsage
                    )
                }
                if (!rollup.fromRollupFile) {
                    // A note rather than a SampleDataBanner, which this used to be: that banner is
                    // titled "Sample data", and these are measurements — only the summing was done
                    // here rather than by the kernel. Labelling a real reading as a sample is the
                    // exact confusion the banner exists to prevent.
                    item(key = "smaps-notice") {
                        Text(
                            text = stringResource(R.string.memory_smaps_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Spacing.Small)
                        )
                    }
                }
                item(key = "footprint") { FootprintCard(rollup) }
            }

            item(key = "regions-header") {
                SectionHeader(
                    title = stringResource(R.string.memory_regions_section),
                    subtitle = pluralStringResource(
                        R.plurals.memory_regions_subtitle,
                        memory.totalRegions,
                        memory.totalRegions
                    ),
                    icon = Icons.Default.Layers
                )
            }
            if (memory.reservedRegions > 0) {
                item(key = "reserved") {
                    Text(
                        text = stringResource(
                            R.string.memory_reserved,
                            formatKilobytes(memory.reservedKb),
                            memory.reservedRegions
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.Small)
                    )
                }
            }
            items(memory.categories, key = { category -> category.key }) { category ->
                CategoryCard(category = category, mappedKb = memory.mappedKb)
            }

            item(key = "heap-header") {
                SectionHeader(
                    title = stringResource(R.string.memory_heap_section),
                    subtitle = stringResource(R.string.memory_heap_subtitle),
                    icon = Icons.Default.Memory
                )
            }
            item(key = "heap") { ByteReadingsCard(memory.malloc, MallocLabels) }

            if (memory.status.isNotEmpty()) {
                item(key = "status-header") {
                    SectionHeader(
                        title = stringResource(R.string.memory_status_section),
                        subtitle = stringResource(R.string.memory_status_subtitle),
                        icon = Icons.Default.Widgets
                    )
                }
                item(key = "status") {
                    MonitorCard {
                        memory.status.forEach { (name, value) -> LabeledValue(label = name, value = value) }
                    }
                }
            }

            if (memory.limits.isNotEmpty()) {
                item(key = "limits-header") {
                    SectionHeader(
                        title = stringResource(R.string.memory_limits_section),
                        subtitle = stringResource(R.string.memory_limits_subtitle),
                        icon = Icons.Default.Lock
                    )
                }
                item(key = "limits") { ByteReadingsCard(memory.limits, LimitLabels, countKeys = setOf("open_files")) }
            }
        }
    }
}

@Composable
private fun FootprintCard(rollup: MemoryRollup, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        Text(
            text = formatKilobytes(rollup.pssKb),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.memory_pss),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.Small))

        LabeledValue(label = stringResource(R.string.memory_rss), value = formatKilobytes(rollup.rssKb))
        LabeledValue(label = stringResource(R.string.memory_private_dirty), value = formatKilobytes(rollup.privateDirtyKb))
        LabeledValue(label = stringResource(R.string.memory_private_clean), value = formatKilobytes(rollup.privateCleanKb))
        LabeledValue(label = stringResource(R.string.memory_shared_dirty), value = formatKilobytes(rollup.sharedDirtyKb))
        LabeledValue(label = stringResource(R.string.memory_shared_clean), value = formatKilobytes(rollup.sharedCleanKb))
        LabeledValue(label = stringResource(R.string.memory_swap), value = formatKilobytes(rollup.swapKb))
        LabeledValue(label = stringResource(R.string.memory_swap_pss), value = formatKilobytes(rollup.swapPssKb))
    }
}

/**
 * One kind of mapping, as a share of the mapped total.
 *
 * The bar is what makes the list readable at a glance: the same seven categories appear on every
 * device, and it is their proportions, not their byte counts, that differ.
 */
@Composable
private fun CategoryCard(category: RegionCategory, mappedKb: Long, modifier: Modifier = Modifier) {
    val share = if (mappedKb > 0) category.sizeKb.toFloat() / mappedKb.toFloat() else 0f

    MonitorCard(modifier = modifier) {
        Text(
            text = stringResource(categoryLabel(category.key)),
            style = MaterialTheme.typography.titleMedium
        )
        LinearProgressIndicator(
            progress = { share },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.ExtraSmall)
        )
        Text(
            text = stringResource(
                R.string.memory_category_detail,
                formatKilobytes(category.sizeKb),
                category.count,
                (share * 100).toInt()
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A card of named byte counts.
 *
 * The keys are listed by the caller rather than taken from the map's own iteration order, which
 * `JSONObject` does not promise to preserve. Anything the native side sends that is not named here
 * is left out on purpose: an unlabelled key would reach the screen as a bare identifier.
 */
@Composable
private fun ByteReadingsCard(
    readings: Map<String, Long>,
    labels: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    countKeys: Set<String> = emptySet()
) {
    MonitorCard(modifier = modifier) {
        labels.forEach { (key, labelRes) ->
            val value = readings[key] ?: return@forEach
            LabeledValue(
                label = stringResource(labelRes),
                value = if (key in countKeys) {
                    stringResource(R.string.memory_count, value)
                } else {
                    formatBytes(value)
                }
            )
        }
    }
}

private val MallocLabels = listOf(
    "in_use" to R.string.memory_malloc_in_use,
    "free" to R.string.memory_malloc_free,
    "arena" to R.string.memory_malloc_arena,
    "mmapped" to R.string.memory_malloc_mmapped,
    "peak" to R.string.memory_malloc_peak,
    "releasable" to R.string.memory_malloc_releasable,
    "free_chunks" to R.string.memory_malloc_free_chunks
)

private val LimitLabels = listOf(
    "address_space" to R.string.memory_limit_address_space,
    "data" to R.string.memory_limit_data,
    "stack" to R.string.memory_limit_stack,
    "open_files" to R.string.memory_limit_open_files
)

/** The display name for a category key the native collector emits. */
@StringRes
private fun categoryLabel(key: String): Int = when (key) {
    "native_lib" -> R.string.memory_category_native_lib
    "art" -> R.string.memory_category_art
    "dalvik" -> R.string.memory_category_dalvik
    "native_heap" -> R.string.memory_category_native_heap
    "stack" -> R.string.memory_category_stack
    "anon" -> R.string.memory_category_anon
    else -> R.string.memory_category_other
}

@Preview(name = "Memory map", showBackground = true)
@Preview(name = "Memory map (dark)", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MemoryMapPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        MemoryMapContent(
            uiState = MemoryMapUiState(
                memory = MemorySnapshot(
                    rollup = MemoryRollup(
                        rssKb = 98_304,
                        pssKb = 74_120,
                        privateCleanKb = 4_096,
                        privateDirtyKb = 51_200,
                        sharedCleanKb = 40_960,
                        sharedDirtyKb = 2_048,
                        swapKb = 0,
                        swapPssKb = 0,
                        fromRollupFile = true
                    ),
                    status = mapOf("VmSize" to "14 GB", "VmRSS" to "98304 kB", "Threads" to "21"),
                    totalRegions = 1832,
                    categories = listOf(
                        RegionCategory("native_lib", 412, 198_340),
                        RegionCategory("anon", 900, 40_960),
                        RegionCategory("native_heap", 40, 22_528)
                    ),
                    malloc = mapOf("in_use" to 6_291_456L, "free" to 2_097_152L, "arena" to 8_388_608L),
                    limits = mapOf("open_files" to 32_768L, "stack" to 8_388_608L)
                ),
                hasLoaded = true
            ),
            onNavigateBack = {}
        )
    }
}
