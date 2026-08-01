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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aoscoremonitor.R
import com.aoscoremonitor.diagnostics.formatBytes
import com.aoscoremonitor.diagnostics.jni.DescriptorAccess
import com.aoscoremonitor.diagnostics.jni.DescriptorKind
import com.aoscoremonitor.diagnostics.jni.DescriptorSnapshot
import com.aoscoremonitor.diagnostics.jni.OpenDescriptor
import com.aoscoremonitor.diagnostics.jni.Unavailable
import com.aoscoremonitor.ui.components.FullScreenMessage
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.MonitorTag
import com.aoscoremonitor.ui.components.ReadingStatus
import com.aoscoremonitor.ui.components.SectionHeader
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.MonitorPreviews
import com.aoscoremonitor.ui.theme.MonitorTypography
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.DescriptorsUiState
import com.aoscoremonitor.ui.viewmodel.DescriptorsViewModel

@Composable
fun DescriptorsScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier, viewModel: DescriptorsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DescriptorsContent(uiState = uiState, onNavigateBack = onNavigateBack, modifier = modifier)
}

@Composable
private fun DescriptorsContent(uiState: DescriptorsUiState, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    MonitorScaffold(
        title = stringResource(R.string.descriptors_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) { innerPadding ->
        val snapshot = uiState.snapshot
        if (snapshot == null || snapshot.descriptors.isEmpty()) {
            FullScreenMessage(
                message = stringResource(emptyMessage(uiState.hasLoaded, snapshot)),
                icon = Icons.Default.Description,
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
                    title = stringResource(R.string.descriptors_summary_section),
                    icon = Icons.Default.Inventory
                )
            }
            item(key = "summary") { SummaryCard(snapshot) }

            item(key = "list-header") {
                SectionHeader(
                    title = stringResource(R.string.descriptors_list_section),
                    subtitle = stringResource(R.string.descriptors_list_subtitle),
                    icon = Icons.Default.Description
                )
            }
            items(snapshot.descriptors, key = { descriptor -> descriptor.fd }) { descriptor ->
                DescriptorCard(descriptor)
            }
        }
    }
}

/**
 * How full the descriptor table is, and what is filling it.
 *
 * The bar is against the soft limit rather than the hard one: the soft limit is what a further
 * `open` is refused at, and a process that raises it has to mean to. It turns amber at nine tenths
 * through [ReadingStatus], the same threshold the storage screen warns at.
 */
@Composable
private fun SummaryCard(snapshot: DescriptorSnapshot, modifier: Modifier = Modifier) {
    val fraction = snapshot.usedFraction
    val status = if (fraction != null && fraction >= NEARLY_FULL) ReadingStatus.Warning else ReadingStatus.Neutral

    MonitorCard(modifier = modifier, status = status) {
        Text(
            text = pluralStringResource(R.plurals.descriptors_open, snapshot.count, snapshot.count),
            style = MaterialTheme.typography.headlineSmall
        )

        val softLimit = snapshot.softLimit
        if (fraction != null && softLimit != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.ExtraSmall)
            )
            Text(
                text = stringResource(R.string.descriptors_limit, snapshot.count, softLimit),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(
                text = stringResource(R.string.descriptors_no_limit),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        snapshot.hardLimit?.let { hardLimit ->
            Text(
                text = stringResource(R.string.descriptors_hard_limit, hardLimit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.ExtraSmall),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            snapshot.breakdown.forEach { (kind, count) ->
                MonitorTag(stringResource(R.string.count_with_name, count, stringResource(kindLabel(kind))))
            }
        }
    }
}

/**
 * One descriptor.
 *
 * The file name leads and the directory follows underneath, because a process holds a dozen
 * descriptors on the same /apex directory and the part that differs is the part worth reading
 * first. Anything that is not a path — `socket:[12345]`, `anon_inode:[eventfd]` — has no file name
 * to split off and is shown whole.
 */
@Composable
private fun DescriptorCard(descriptor: OpenDescriptor, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = descriptor.displayName.ifEmpty { stringResource(targetReason(descriptor.targetUnavailable)) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = Spacing.Small)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                MonitorTag(stringResource(R.string.descriptors_number, descriptor.fd))
                MonitorTag(stringResource(kindLabel(descriptor.kind)))
            }
        }

        if (descriptor.directory.isNotEmpty()) {
            Text(
                text = descriptor.directory,
                style = MonitorTypography.machineText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = describe(descriptor),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (descriptor.flags.isNotEmpty()) {
            // Shown as the kernel's own tokens rather than translated: they name the O_ constants
            // the descriptor was opened with, which is what someone reading this screen is matching
            // against their own open() call.
            Text(
                text = descriptor.flags.joinToString(" "),
                style = MonitorTypography.machineText,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The one-line detail row: how it was opened, how big it is, where it is, and whether it survives
 * an exec.
 *
 * Assembled from the parts that were actually read, so a socket — which has no size and no offset —
 * gets a short line rather than a line of blanks.
 */
@Composable
private fun describe(descriptor: OpenDescriptor): String {
    val parts = listOfNotNull(
        stringResource(accessLabel(descriptor.access)),
        // What fstat would have said. Without this the card silently reads "Other" with no inode,
        // which looks like a descriptor on nothing rather than like a refused reading.
        if (descriptor.type == null) stringResource(statReason(descriptor.typeUnavailable)) else null,
        descriptor.sizeBytes?.let { formatBytes(it) },
        descriptor.offset?.let { stringResource(R.string.descriptors_offset, it) },
        descriptor.inode?.let { stringResource(R.string.descriptors_inode, it) },
        stringResource(if (descriptor.closeOnExec) R.string.descriptors_cloexec else R.string.descriptors_kept_on_exec)
    )
    return parts.joinToString(SEPARATOR)
}

@StringRes
private fun emptyMessage(hasLoaded: Boolean, snapshot: DescriptorSnapshot?): Int = when {
    !hasLoaded -> R.string.descriptors_loading
    snapshot == null -> R.string.descriptors_unavailable
    else -> R.string.descriptors_empty
}

@StringRes
private fun kindLabel(kind: DescriptorKind): Int = when (kind) {
    DescriptorKind.File -> R.string.descriptors_kind_file
    DescriptorKind.Directory -> R.string.descriptors_kind_directory
    DescriptorKind.Socket -> R.string.descriptors_kind_socket
    DescriptorKind.Pipe -> R.string.descriptors_kind_pipe
    DescriptorKind.AnonInode -> R.string.descriptors_kind_anon
    DescriptorKind.Device -> R.string.descriptors_kind_device
    DescriptorKind.Other -> R.string.descriptors_kind_other
}

@StringRes
private fun accessLabel(access: DescriptorAccess): Int = when (access) {
    DescriptorAccess.Read -> R.string.descriptors_access_read
    DescriptorAccess.Write -> R.string.descriptors_access_write
    DescriptorAccess.ReadWrite -> R.string.descriptors_access_readwrite
    DescriptorAccess.Unknown -> R.string.descriptors_access_unknown
}

/** Why the kind and inode are missing, when fstat said why. */
@StringRes
private fun statReason(reason: Unavailable?): Int = when (reason) {
    Unavailable.Denied -> R.string.descriptors_stat_denied
    Unavailable.Absent -> R.string.descriptors_stat_absent
    else -> R.string.descriptors_stat_unavailable
}

/** Why the target is missing, when readlink said why. */
@StringRes
private fun targetReason(reason: Unavailable?): Int = when (reason) {
    Unavailable.Denied -> R.string.descriptors_target_denied
    else -> R.string.descriptors_target_unavailable
}

private const val NEARLY_FULL = 0.9f
private const val SEPARATOR = " · "

@MonitorPreviews
@Composable
private fun DescriptorsPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        DescriptorsContent(
            uiState = DescriptorsUiState(
                snapshot = DescriptorSnapshot(
                    descriptors = listOf(
                        OpenDescriptor(
                            fd = 0,
                            target = "/dev/null",
                            type = "character",
                            access = DescriptorAccess.ReadWrite,
                            inode = 6,
                            offset = 0
                        ),
                        OpenDescriptor(
                            fd = 12,
                            target = "/apex/com.android.art/javalib/core-oj.jar",
                            type = "regular",
                            access = DescriptorAccess.Read,
                            flags = listOf("nonblock"),
                            closeOnExec = true,
                            inode = 288_734,
                            sizeBytes = 4_194_304,
                            offset = 0
                        ),
                        OpenDescriptor(
                            fd = 27,
                            target = "socket:[52831]",
                            type = "socket",
                            access = DescriptorAccess.ReadWrite,
                            closeOnExec = true,
                            inode = 52_831
                        ),
                        OpenDescriptor(
                            fd = 31,
                            target = "anon_inode:[eventfd]",
                            type = "regular",
                            access = DescriptorAccess.ReadWrite,
                            flags = listOf("nonblock"),
                            closeOnExec = true,
                            inode = 12_450,
                            offset = 0
                        )
                    ),
                    softLimit = 32_768,
                    hardLimit = 1_048_576
                ),
                hasLoaded = true
            ),
            onNavigateBack = {}
        )
    }
}
