package com.aoscoremonitor.ui.screens.jni

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aoscoremonitor.R
import com.aoscoremonitor.diagnostics.formatBytes
import com.aoscoremonitor.diagnostics.jni.MountPoint
import com.aoscoremonitor.diagnostics.jni.Unavailable
import com.aoscoremonitor.ui.components.FullScreenMessage
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.ReadingStatus
import com.aoscoremonitor.ui.components.SectionHeader
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.MonitorTypography
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.StorageMountsUiState
import com.aoscoremonitor.ui.viewmodel.StorageMountsViewModel

@Composable
fun StorageMountsScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier, viewModel: StorageMountsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    StorageMountsContent(uiState = uiState, onNavigateBack = onNavigateBack, modifier = modifier)
}

@Composable
private fun StorageMountsContent(uiState: StorageMountsUiState, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    MonitorScaffold(
        title = stringResource(R.string.storage_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) { innerPadding ->
        if (uiState.filesystems.isEmpty() && uiState.pseudoFilesystems.isEmpty()) {
            FullScreenMessage(
                message = stringResource(if (uiState.hasLoaded) R.string.storage_empty else R.string.storage_loading),
                icon = Icons.Default.Storage,
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
            item(key = "filesystems-header") {
                SectionHeader(
                    title = stringResource(R.string.storage_filesystems_section),
                    subtitle = stringResource(R.string.storage_filesystems_subtitle),
                    icon = Icons.Default.Storage
                )
            }
            items(uiState.filesystems, key = { mount -> mount.target }) { mount -> MountCard(mount) }

            if (uiState.pseudoFilesystems.isNotEmpty()) {
                item(key = "pseudo-header") {
                    SectionHeader(
                        title = stringResource(R.string.storage_pseudo_section),
                        subtitle = stringResource(R.string.storage_pseudo_subtitle),
                        icon = Icons.Default.Memory
                    )
                }
                items(uiState.pseudoFilesystems, key = { mount -> "pseudo-" + mount.target }) { mount ->
                    PseudoMountRow(mount)
                }
            }
        }
    }
}

/**
 * One filesystem, with what is left on it.
 *
 * The bar turns amber past nine tenths full through [ReadingStatus], which is the same threshold
 * and the same colour the rest of the app uses for a reading that is working but worth watching.
 */
@Composable
private fun MountCard(mount: MountPoint, modifier: Modifier = Modifier) {
    val fraction = mount.usedFraction
    val status = when {
        fraction == null -> ReadingStatus.Neutral
        fraction >= NEARLY_FULL -> ReadingStatus.Warning
        else -> ReadingStatus.Neutral
    }

    MonitorCard(modifier = modifier, status = status) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = mount.target,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = Spacing.Small)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                if (mount.readOnly) MountTag(stringResource(R.string.storage_readonly))
                MountTag(mount.fsType)
            }
        }

        val total = mount.totalBytes
        val used = mount.usedBytes
        if (fraction != null && total != null && used != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.ExtraSmall)
            )
            Text(
                text = stringResource(R.string.storage_usage, formatBytes(used), formatBytes(total)),
                style = MaterialTheme.typography.bodyMedium
            )
            mount.availableBytes?.let {
                Text(
                    text = stringResource(R.string.storage_available, formatBytes(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = stringResource(unmeasurableReason(mount.capacityUnavailable)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (mount.inodesTotal != null && mount.inodesFree != null && mount.inodesTotal > 0) {
            Text(
                text = stringResource(R.string.storage_inodes, mount.inodesFree.toString(), mount.inodesTotal.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = stringResource(R.string.storage_source, mount.source),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = mount.options,
            style = MonitorTypography.machineText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = OPTION_LINES,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A pseudo filesystem has no capacity to report, so it gets a line rather than a card of its own. */
@Composable
private fun PseudoMountRow(mount: MountPoint, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = mount.target,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = Spacing.Small)
            )
            MountTag(mount.fsType)
        }
    }
}

@Composable
private fun MountTag(text: String, modifier: Modifier = Modifier) {
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
            modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.ExtraSmall)
        )
    }
}

/** Why the capacity is missing, when statvfs said why. */
@StringRes
private fun unmeasurableReason(reason: Unavailable?): Int = when (reason) {
    Unavailable.Denied -> R.string.storage_unmeasurable_denied
    Unavailable.Absent -> R.string.storage_unmeasurable_absent
    else -> R.string.storage_unmeasurable
}

private const val NEARLY_FULL = 0.9f
private const val OPTION_LINES = 2

@Preview(name = "Storage", showBackground = true)
@Preview(name = "Storage (dark)", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StorageMountsPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        StorageMountsContent(
            uiState = StorageMountsUiState(
                filesystems = listOf(
                    MountPoint(
                        source = "/dev/block/dm-9",
                        target = "/data",
                        fsType = "f2fs",
                        options = "rw,nosuid,nodev,noatime,background_gc=on,discard,inline_xattr",
                        readOnly = false,
                        totalBytes = 107_374_182_400,
                        freeBytes = 42_949_672_960,
                        availableBytes = 40_000_000_000,
                        inodesTotal = 26_214_400,
                        inodesFree = 25_000_000
                    ),
                    MountPoint(
                        source = "/dev/block/dm-0",
                        target = "/",
                        fsType = "erofs",
                        options = "ro,seclabel,relatime",
                        readOnly = true,
                        totalBytes = 4_294_967_296,
                        freeBytes = 0,
                        availableBytes = 0
                    )
                ),
                pseudoFilesystems = listOf(
                    MountPoint("proc", "/proc", "proc", "rw,nosuid,nodev,noexec,relatime", false),
                    MountPoint("sysfs", "/sys", "sysfs", "rw,seclabel,nosuid,nodev,noexec,relatime", false)
                ),
                hasLoaded = true
            ),
            onNavigateBack = {}
        )
    }
}
