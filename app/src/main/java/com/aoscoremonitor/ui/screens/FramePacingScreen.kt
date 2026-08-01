package com.aoscoremonitor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aoscoremonitor.R
import com.aoscoremonitor.diagnostics.DisplayInfo
import com.aoscoremonitor.diagnostics.DisplayMode
import com.aoscoremonitor.diagnostics.DisplayState
import com.aoscoremonitor.diagnostics.FramePacing
import com.aoscoremonitor.ui.components.FullScreenMessage
import com.aoscoremonitor.ui.components.LabeledValue
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.ReadingStatus
import com.aoscoremonitor.ui.components.SectionHeader
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.MonitorPreviews
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.FramePacingUiState
import com.aoscoremonitor.ui.viewmodel.FramePacingViewModel
import com.aoscoremonitor.ui.viewmodel.monitorViewModel

@Composable
fun FramePacingScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FramePacingViewModel = monitorViewModel { FramePacingViewModel(it) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FramePacingContent(uiState = uiState, onNavigateBack = onNavigateBack, modifier = modifier)
}

@Composable
private fun FramePacingContent(uiState: FramePacingUiState, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    MonitorScaffold(
        title = stringResource(R.string.frames_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) { innerPadding ->
        val display = uiState.display
        if (display == null) {
            FullScreenMessage(
                message = stringResource(if (uiState.hasLoaded) R.string.frames_unavailable else R.string.frames_loading),
                icon = Icons.Default.Speed,
                modifier = Modifier.padding(innerPadding)
            )
            return@MonitorScaffold
        }

        val pacing = uiState.pacing

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            item(key = "rate-header") {
                SectionHeader(
                    title = stringResource(R.string.frames_rate_section),
                    subtitle = stringResource(R.string.frames_rate_subtitle),
                    icon = Icons.Default.Speed
                )
            }
            item(key = "rate") { RateCard(display = display, pacing = pacing) }

            item(key = "jank-header") {
                SectionHeader(
                    title = stringResource(R.string.frames_jank_section),
                    subtitle = stringResource(R.string.frames_jank_subtitle),
                    icon = Icons.Default.Warning
                )
            }
            item(key = "jank") { JankCard(pacing) }

            if (pacing.distribution.isNotEmpty()) {
                item(key = "distribution-header") {
                    SectionHeader(
                        title = stringResource(R.string.frames_distribution_section),
                        subtitle = stringResource(R.string.frames_distribution_subtitle),
                        icon = Icons.Default.BarChart
                    )
                }
                item(key = "distribution") { DistributionCard(pacing) }
            }

            item(key = "timing-header") {
                SectionHeader(
                    title = stringResource(R.string.frames_timing_section),
                    subtitle = stringResource(R.string.frames_timing_subtitle),
                    icon = Icons.Default.Schedule
                )
            }
            item(key = "timing") { TimingCard(display) }
        }
    }
}

/**
 * The rate frames actually arrived at, against the rate the panel is being driven at.
 *
 * Both numbers, because either alone says nothing: 60 fps is the whole of what a 60 Hz panel can
 * give and half of what a 120 Hz one can.
 */
@Composable
private fun RateCard(display: DisplayInfo, pacing: FramePacing, modifier: Modifier = Modifier) {
    val measured = pacing.measuredFps

    MonitorCard(modifier = modifier) {
        Text(
            text = if (measured == null) {
                stringResource(R.string.frames_waiting)
            } else {
                stringResource(R.string.frames_fps, measured)
            },
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = if (display.refreshRateHz > 0f) {
                stringResource(
                    R.string.frames_target,
                    display.refreshRateHz,
                    stringResource(R.string.frames_millis, millis(display.frameIntervalNanos))
                )
            } else {
                stringResource(R.string.frames_target_unknown)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (pacing.intervals > 0) {
            Text(
                text = stringResource(
                    R.string.frames_counted,
                    pacing.intervals,
                    stringResource(R.string.frames_seconds, seconds(pacing.elapsedNanos))
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * What was missed.
 *
 * The card turns amber once late gaps pass one in twenty, which is where a scroll starts to look
 * uneven rather than merely imperfect.
 */
@Composable
private fun JankCard(pacing: FramePacing, modifier: Modifier = Modifier) {
    val jank = pacing.jankFraction
    val status = if (jank != null && jank >= NOTICEABLE_JANK) ReadingStatus.Warning else ReadingStatus.Neutral

    MonitorCard(modifier = modifier, status = status, spacing = Spacing.Medium) {
        if (pacing.intervals == 0) {
            Text(text = stringResource(R.string.frames_waiting), style = MaterialTheme.typography.bodyMedium)
            return@MonitorCard
        }
        if (pacing.droppedFrames == 0) {
            Text(text = stringResource(R.string.frames_none_missed), style = MaterialTheme.typography.bodyMedium)
            return@MonitorCard
        }

        LabeledValue(
            label = stringResource(R.string.frames_dropped),
            value = pluralStringResource(R.plurals.frames_dropped_value, pacing.droppedFrames, pacing.droppedFrames)
        )
        LabeledValue(
            label = stringResource(R.string.frames_jank_rate),
            value = stringResource(R.string.frames_jank_rate_value, (jank ?: 0f) * PERCENT, pacing.intervals)
        )
        LabeledValue(
            label = stringResource(R.string.frames_worst),
            value = stringResource(
                R.string.frames_worst_value,
                stringResource(R.string.frames_millis, millis(pacing.worstIntervalNanos)),
                pacing.worstSlots
            )
        )
    }
}

/**
 * Every gap, grouped by how many frames wide it was.
 *
 * A bar per width rather than an average: an average hides the shape, and the shape is the reading
 * — a hundred gaps of one frame and one of thirty is a very different experience from a hundred
 * gaps of two.
 */
@Composable
private fun DistributionCard(pacing: FramePacing, modifier: Modifier = Modifier) {
    val total = pacing.intervals.coerceAtLeast(1)

    MonitorCard(modifier = modifier) {
        pacing.distribution.forEach { (slots, count) ->
            val share = count.toFloat() / total

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.ExtraSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (slots == 1) {
                        stringResource(R.string.frames_slot_on_time)
                    } else {
                        pluralStringResource(R.plurals.frames_slot_label, slots, slots)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.frames_slot_count, count, (share * PERCENT).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { share },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.ExtraSmall)
            )
        }
    }
}

/**
 * How far ahead of the panel the app is asked to work.
 *
 * Both come from the display rather than from the measurement: the offset is when the app's vsync
 * is delivered relative to the panel's, and the deadline is when a frame has to be finished to make
 * that scan-out. They are the budget every gap above is measured against.
 */
@Composable
private fun TimingCard(display: DisplayInfo, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier, spacing = Spacing.Medium) {
        LabeledValue(
            label = stringResource(R.string.frames_vsync_offset),
            value = stringResource(R.string.frames_millis, millis(display.appVsyncOffsetNanos))
        )
        LabeledValue(
            label = stringResource(R.string.frames_presentation_deadline),
            value = stringResource(R.string.frames_millis, millis(display.presentationDeadlineNanos))
        )
    }
}

private fun millis(nanos: Long): Float = nanos / NANOS_PER_MILLI

private fun seconds(nanos: Long): Float = nanos / NANOS_PER_SECOND

private const val NANOS_PER_MILLI = 1_000_000f
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val PERCENT = 100f
private const val NOTICEABLE_JANK = 0.05f

@MonitorPreviews
@Composable
private fun FramePacingPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        FramePacingContent(
            uiState = FramePacingUiState(
                display = DisplayInfo(
                    name = "Built-in Screen",
                    displayId = 0,
                    state = DisplayState.On,
                    rotationDegrees = 0,
                    currentMode = DisplayMode(1, 1080, 2400, 60f),
                    appVsyncOffsetNanos = 1_000_000,
                    presentationDeadlineNanos = 12_000_000
                ),
                pacing = FramePacing(
                    periodNanos = 16_666_666,
                    intervals = 412,
                    elapsedNanos = 7_100_000_000,
                    worstIntervalNanos = 83_000_000,
                    histogram = mapOf(1 to 396, 2 to 12, 3 to 3, 5 to 1)
                ),
                hasLoaded = true
            ),
            onNavigateBack = {}
        )
    }
}
