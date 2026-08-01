package com.aoscoremonitor.ui.screens.jni

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.aoscoremonitor.diagnostics.jni.LoadedModule
import com.aoscoremonitor.diagnostics.jni.ModuleSnapshot
import com.aoscoremonitor.ui.components.FullScreenMessage
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.SectionHeader
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.MonitorPreviews
import com.aoscoremonitor.ui.theme.MonitorTypography
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.LoadedLibrariesUiState
import com.aoscoremonitor.ui.viewmodel.LoadedLibrariesViewModel

@Composable
fun LoadedLibrariesScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier, viewModel: LoadedLibrariesViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LoadedLibrariesContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRefresh = viewModel::refresh,
        modifier = modifier
    )
}

@Composable
private fun LoadedLibrariesContent(
    uiState: LoadedLibrariesUiState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    MonitorScaffold(
        title = stringResource(R.string.libraries_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        floatingActionButton = {
            // This screen does not poll — the loaded set barely changes — so taking a new reading
            // has to be something the user can ask for.
            FloatingActionButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.action_refresh)
                )
            }
        }
    ) { innerPadding ->
        val snapshot = uiState.modules
        if (snapshot == null || snapshot.modules.isEmpty()) {
            FullScreenMessage(
                message = stringResource(if (uiState.hasLoaded) R.string.libraries_unavailable else R.string.libraries_loading),
                icon = Icons.Default.Layers,
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
                    title = stringResource(R.string.libraries_summary_section),
                    subtitle = stringResource(
                        R.string.libraries_summary,
                        snapshot.modules.size,
                        formatBytes(snapshot.totalMappedSize)
                    ),
                    icon = Icons.Default.Layers
                )
            }
            if (snapshot.loadEvents != null && snapshot.unloadEvents != null) {
                item(key = "events") {
                    Text(
                        text = stringResource(R.string.libraries_load_events, snapshot.loadEvents, snapshot.unloadEvents),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.Small)
                    )
                }
            }
            items(snapshot.modules, key = { module -> module.baseAddress + module.path }) { module ->
                ModuleCard(module)
            }
        }
    }
}

@Composable
private fun ModuleCard(module: LoadedModule, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (module.isMainExecutable) stringResource(R.string.libraries_main_executable) else module.fileName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = Spacing.Small)
            )
            Text(
                text = formatBytes(module.mappedSize),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (module.directory.isNotEmpty()) {
            Text(
                text = module.directory,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                // The interesting end of a library path is the right-hand one, but the file name is
                // already on the line above, so the head is what is worth keeping here.
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = module.baseAddress,
            style = MonitorTypography.machineText,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = module.buildId?.let { stringResource(R.string.libraries_build_id, it.take(BUILD_ID_DIGITS)) }
                ?: stringResource(R.string.libraries_no_build_id),
            style = MonitorTypography.machineText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.padding(top = Spacing.ExtraSmall),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            if (module.hasRelro) ModuleTag(stringResource(R.string.libraries_relro))
            if (module.hasTls) ModuleTag(stringResource(R.string.libraries_tls))
            ModuleTag(pluralStringResource(R.plurals.libraries_segments, module.segmentCount, module.segmentCount))
        }
    }
}

@Composable
private fun ModuleTag(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.ExtraSmall)
        )
    }
}

/** Enough of a build id to tell two builds apart without wrapping the line. */
private const val BUILD_ID_DIGITS = 16

@MonitorPreviews
@Composable
private fun LoadedLibrariesPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        LoadedLibrariesContent(
            uiState = LoadedLibrariesUiState(
                modules = ModuleSnapshot(
                    modules = listOf(
                        LoadedModule(
                            path = "/apex/com.android.art/lib64/libart.so",
                            baseAddress = "0x7b1c2a0000",
                            mappedSize = 8_912_896,
                            buildId = "9f2c14ad55e0b3771c0e4a8d2b7f6613",
                            hasRelro = true,
                            hasTls = true,
                            segmentCount = 4
                        ),
                        LoadedModule(
                            path = "/apex/com.android.runtime/lib64/bionic/libc.so",
                            baseAddress = "0x7b0f440000",
                            mappedSize = 1_052_672,
                            buildId = "1ab3ff90c7d5e2418800aa11bc93de77",
                            hasRelro = true,
                            hasTls = true,
                            segmentCount = 4
                        )
                    ),
                    loadEvents = 231,
                    unloadEvents = 3
                ),
                hasLoaded = true
            ),
            onNavigateBack = {},
            onRefresh = {}
        )
    }
}
