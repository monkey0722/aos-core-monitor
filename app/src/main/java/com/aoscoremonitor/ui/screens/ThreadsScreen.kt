package com.aoscoremonitor.ui.screens

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
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.aoscoremonitor.diagnostics.jni.SchedulerPolicy
import com.aoscoremonitor.diagnostics.jni.ThreadInfo
import com.aoscoremonitor.diagnostics.jni.ThreadSnapshot
import com.aoscoremonitor.diagnostics.jni.ThreadState
import com.aoscoremonitor.diagnostics.jni.Unavailable
import com.aoscoremonitor.ui.components.FullScreenMessage
import com.aoscoremonitor.ui.components.LabeledValue
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.SectionHeader
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.MonitorPreviews
import com.aoscoremonitor.ui.theme.MonitorTypography
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.ThreadsUiState
import com.aoscoremonitor.ui.viewmodel.ThreadsViewModel

@Composable
fun ThreadsScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ThreadsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ThreadsContent(uiState = uiState, onNavigateBack = onNavigateBack, modifier = modifier)
}

@Composable
private fun ThreadsContent(uiState: ThreadsUiState, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    MonitorScaffold(
        title = stringResource(R.string.threads_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) { innerPadding ->
        val snapshot = uiState.snapshot
        if (snapshot == null || snapshot.threads.isEmpty()) {
            FullScreenMessage(
                message = stringResource(if (uiState.hasLoaded) R.string.threads_unavailable else R.string.threads_loading),
                icon = Icons.Default.AccountTree,
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
                    title = stringResource(R.string.threads_summary_section),
                    subtitle = pluralStringResource(
                        R.plurals.threads_summary,
                        snapshot.threads.size,
                        snapshot.threads.size
                    ),
                    icon = Icons.Default.AccountTree
                )
            }
            item(key = "summary") { SummaryCard(snapshot) }

            item(key = "threads-header") {
                SectionHeader(
                    title = stringResource(R.string.threads_section),
                    subtitle = stringResource(R.string.threads_subtitle),
                    icon = Icons.Default.Schedule
                )
            }
            items(snapshot.threads, key = { thread -> thread.tid }) { thread ->
                ThreadCard(thread = thread, snapshot = snapshot)
            }
        }
    }
}

/**
 * What every thread here shares.
 *
 * Deliberately not a second copy of the CPU screen's core count: what belongs here is the mask this
 * process was given, which is a scheduling fact about the process rather than a fact about the CPU.
 */
@Composable
private fun SummaryCard(snapshot: ThreadSnapshot, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        val realTime = snapshot.threads.count { it.policy.isRealTime }
        LabeledValue(
            label = stringResource(R.string.threads_allowed_cpus),
            value = snapshot.processAffinity?.takeIf { it.isNotEmpty() }
                ?: stringResource(affinityReason(snapshot.processAffinityUnavailable))
        )
        LabeledValue(
            label = stringResource(R.string.threads_real_time),
            value = pluralStringResource(R.plurals.threads_real_time_count, realTime, realTime)
        )
    }
}

@Composable
private fun ThreadCard(thread: ThreadInfo, snapshot: ThreadSnapshot, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                // A thread with no name is possible — comm is empty for a moment after clone —
                // so the id stands in rather than leaving the row headless.
                text = thread.name.ifEmpty { stringResource(R.string.threads_unnamed, thread.tid) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = Spacing.Small)
            )
            Text(
                text = stringResource(R.string.threads_cpu_time, snapshot.cpuMillis(thread)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.padding(vertical = Spacing.ExtraSmall),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            ThreadTag(stringResource(thread.state.labelRes))
            ThreadTag(stringResource(thread.policy.labelRes))
        }

        Text(
            text = stringResource(R.string.threads_identity, thread.tid),
            style = MonitorTypography.machineText,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Nice and priority are one line: they are two views of the same number for an ordinary
        // thread, and reading them apart tells nobody anything.
        val nice = thread.nice
        if (nice != null) {
            LabeledValue(
                label = stringResource(R.string.threads_nice),
                value = thread.realTimePriority?.takeIf { it > 0 }
                    ?.let { stringResource(R.string.threads_nice_with_rt, nice, it) }
                    ?: nice.toString()
            )
        }

        val affinity = thread.affinity
        if (!affinity.isNullOrEmpty() && affinity != snapshot.processAffinity) {
            // Only when it differs from the process mask: repeating "0-7" on every thread of an
            // eight-core device is a column of noise.
            LabeledValue(label = stringResource(R.string.threads_allowed_cpus), value = affinity)
        } else if (affinity == null && thread.affinityUnavailable != null) {
            // A thread that exited between the listing and the call answers ESRCH, which is worth
            // a line rather than a row that silently omits the mask.
            LabeledValue(
                label = stringResource(R.string.threads_allowed_cpus),
                value = stringResource(affinityReason(thread.affinityUnavailable))
            )
        }

        thread.lastCpu?.let {
            LabeledValue(label = stringResource(R.string.threads_last_cpu), value = it.toString())
        }
    }
}

@Composable
private fun ThreadTag(text: String, modifier: Modifier = Modifier) {
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

private val ThreadState.labelRes: Int
    @StringRes
    get() = when (this) {
        ThreadState.Running -> R.string.thread_state_running
        ThreadState.Sleeping -> R.string.thread_state_sleeping
        ThreadState.DiskSleep -> R.string.thread_state_disk_sleep
        ThreadState.Stopped -> R.string.thread_state_stopped
        ThreadState.TracingStop -> R.string.thread_state_tracing
        ThreadState.Zombie -> R.string.thread_state_zombie
        ThreadState.Idle -> R.string.thread_state_idle
        ThreadState.Unknown -> R.string.thread_state_unknown
    }

private val SchedulerPolicy.labelRes: Int
    @StringRes
    get() = when (this) {
        SchedulerPolicy.Other -> R.string.thread_policy_other
        SchedulerPolicy.Fifo -> R.string.thread_policy_fifo
        SchedulerPolicy.RoundRobin -> R.string.thread_policy_rr
        SchedulerPolicy.Batch -> R.string.thread_policy_batch
        SchedulerPolicy.Idle -> R.string.thread_policy_idle
        SchedulerPolicy.Deadline -> R.string.thread_policy_deadline
        SchedulerPolicy.Unknown -> R.string.thread_policy_unknown
    }

/** Why the CPU mask is missing, when sched_getaffinity said why. */
@StringRes
private fun affinityReason(reason: Unavailable?): Int = when (reason) {
    Unavailable.Denied -> R.string.threads_affinity_denied
    Unavailable.Absent -> R.string.threads_affinity_absent
    else -> R.string.threads_affinity_unavailable
}

@MonitorPreviews
@Composable
private fun ThreadsPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        ThreadsContent(
            uiState = ThreadsUiState(
                snapshot = ThreadSnapshot(
                    threads = listOf(
                        ThreadInfo(
                            tid = 4821,
                            name = "scoremonitor",
                            state = ThreadState.Running,
                            userTicks = 120,
                            systemTicks = 31,
                            priority = 20,
                            nice = 0,
                            policy = SchedulerPolicy.Other,
                            lastCpu = 3,
                            affinity = "0-7"
                        ),
                        ThreadInfo(
                            tid = 4855,
                            name = "RenderThread",
                            state = ThreadState.Sleeping,
                            userTicks = 90,
                            systemTicks = 14,
                            priority = 16,
                            nice = -4,
                            policy = SchedulerPolicy.Other,
                            lastCpu = 6,
                            affinity = "4-7"
                        ),
                        ThreadInfo(
                            tid = 4870,
                            name = "HeapTaskDaemon",
                            state = ThreadState.Sleeping,
                            userTicks = 4,
                            systemTicks = 1,
                            priority = 20,
                            nice = 0,
                            policy = SchedulerPolicy.Other,
                            lastCpu = 0,
                            affinity = "0-7"
                        )
                    ),
                    clockTicks = 100,
                    processAffinity = "0-7"
                ),
                hasLoaded = true
            ),
            onNavigateBack = {}
        )
    }
}
