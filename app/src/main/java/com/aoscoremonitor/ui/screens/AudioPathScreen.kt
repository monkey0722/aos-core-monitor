package com.aoscoremonitor.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aoscoremonitor.R
import com.aoscoremonitor.diagnostics.jni.AudioGranted
import com.aoscoremonitor.diagnostics.jni.AudioHardware
import com.aoscoremonitor.diagnostics.jni.AudioPathSnapshot
import com.aoscoremonitor.diagnostics.jni.AudioPerformanceMode
import com.aoscoremonitor.diagnostics.jni.AudioProbe
import com.aoscoremonitor.diagnostics.jni.AudioRequest
import com.aoscoremonitor.diagnostics.jni.AudioSharingMode
import com.aoscoremonitor.ui.components.FullScreenMessage
import com.aoscoremonitor.ui.components.LabeledValue
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.MonitorTag
import com.aoscoremonitor.ui.components.ReadingStatus
import com.aoscoremonitor.ui.components.SectionHeader
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.MonitorPreviews
import com.aoscoremonitor.ui.theme.MonitorTypography
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.AudioPathUiState
import com.aoscoremonitor.ui.viewmodel.AudioPathViewModel

@Composable
fun AudioPathScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier, viewModel: AudioPathViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AudioPathContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRefresh = viewModel::refresh,
        modifier = modifier
    )
}

@Composable
private fun AudioPathContent(uiState: AudioPathUiState, onNavigateBack: () -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    MonitorScaffold(
        title = stringResource(R.string.audio_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        floatingActionButton = {
            // Worth offering here in a way it is not on the other native screens: what the system
            // grants depends on what else is playing, so the answer can change between taps.
            FloatingActionButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.action_refresh)
                )
            }
        }
    ) { innerPadding ->
        val snapshot = uiState.audio
        if (snapshot == null || snapshot.probes.isEmpty()) {
            FullScreenMessage(
                message = stringResource(
                    when {
                        !uiState.hasLoaded -> R.string.audio_loading
                        snapshot == null -> R.string.audio_unavailable
                        else -> R.string.audio_no_probes
                    }
                ),
                icon = Icons.Default.GraphicEq,
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
                    title = stringResource(R.string.audio_summary_section),
                    subtitle = pluralStringResource(
                        R.plurals.audio_summary,
                        snapshot.probes.size,
                        snapshot.probes.size
                    ),
                    icon = Icons.Default.GraphicEq
                )
            }

            // Said before the readings rather than after them: a screen that opens audio streams
            // owes the reader an account of what it did to the device.
            item(key = "no-playback") {
                Text(
                    text = stringResource(R.string.audio_no_playback_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!snapshot.hardwareQueryAvailable) {
                item(key = "no-hardware-query") {
                    Text(
                        text = stringResource(R.string.audio_hardware_unavailable_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.Small)
                    )
                }
            }

            items(snapshot.probes, key = { probe -> probe.label }) { probe -> ProbeCard(probe) }
        }
    }
}

@Composable
private fun ProbeCard(probe: AudioProbe, modifier: Modifier = Modifier) {
    MonitorCard(
        modifier = modifier,
        // The colour is decoration and nothing more — MonitorCard's status sets the container and
        // content colours and adds no semantics, so it says nothing to a screen reader. What
        // carries the refusal is the sentence in the card body, which is why that sentence is
        // there rather than left to the shade of the card.
        status = if (probe.opened) ReadingStatus.Neutral else ReadingStatus.Warning
    ) {
        Text(
            text = stringResource(probe.labelRes),
            style = MaterialTheme.typography.titleMedium
        )
        LabeledValue(
            label = stringResource(R.string.audio_requested),
            value = probe.requested.describe()
        )

        if (!probe.opened) {
            Text(
                text = stringResource(R.string.audio_refused),
                style = MaterialTheme.typography.bodyMedium
            )
            if (probe.openError != null) {
                Text(
                    text = probe.openError,
                    style = MonitorTypography.machineText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@MonitorCard
        }

        val granted = probe.granted ?: return@MonitorCard
        if (probe.grantedAsRequested) {
            Text(
                text = stringResource(R.string.audio_as_requested),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Substitutions are spelled out in words rather than marked with a colour: a pill that
        // changes shade says nothing to a screen reader, and "shared (asked exclusive)" does.
        NegotiatedRow(
            labelRes = R.string.audio_row_sharing,
            granted = stringResource(granted.sharingMode.labelRes),
            requested = stringResource(probe.requested.sharingMode.labelRes),
            asRequested = probe.sharingModeAsRequested
        )
        NegotiatedRow(
            labelRes = R.string.audio_row_performance,
            granted = stringResource(granted.performanceMode.labelRes),
            requested = stringResource(probe.requested.performanceMode.labelRes),
            asRequested = probe.performanceModeAsRequested
        )
        if (granted.sampleRate != null) {
            NegotiatedRow(
                labelRes = R.string.audio_row_sample_rate,
                granted = stringResource(R.string.audio_hz, granted.sampleRate),
                requested = probe.requested.sampleRate?.let { stringResource(R.string.audio_hz, it) }.orEmpty(),
                asRequested = probe.sampleRateAsRequested
            )
        }

        if (granted.format != null) {
            LabeledValue(
                label = stringResource(R.string.audio_row_format),
                value = granted.format,
                valueStyle = MonitorTypography.machineText
            )
        }
        if (granted.channelCount != null) {
            LabeledValue(
                label = stringResource(R.string.audio_row_channels),
                value = granted.channelCount.toString(),
                valueStyle = MonitorTypography.machineText
            )
        }
        if (granted.framesPerBurst != null) {
            LabeledValue(
                label = stringResource(R.string.audio_row_burst),
                value = granted.framesPerBurst.toString(),
                valueStyle = MonitorTypography.machineText
            )
        }
        if (granted.bufferSize != null && granted.bufferCapacity != null) {
            LabeledValue(
                label = stringResource(R.string.audio_row_buffer),
                value = stringResource(R.string.audio_buffer_value, granted.bufferSize, granted.bufferCapacity),
                valueStyle = MonitorTypography.machineText
            )
        }
        if (granted.deviceId != null) {
            LabeledValue(
                label = stringResource(R.string.audio_row_device),
                value = granted.deviceId.toString(),
                valueStyle = MonitorTypography.machineText
            )
        }

        if (probe.hardware != null) {
            HardwareBlock(probe.hardware, probe.isResampled, probe.isFormatConverted)
        }
    }
}

/**
 * A value the system chose, next to the one that was asked for when the two differ.
 *
 * When they agree there is nothing to compare, so only the value is shown: repeating the request
 * on every row would bury the two rows where the system said no.
 */
@Composable
private fun NegotiatedRow(
    @StringRes labelRes: Int,
    granted: String,
    requested: String,
    asRequested: Boolean,
    modifier: Modifier = Modifier
) {
    LabeledValue(
        label = stringResource(labelRes),
        value = if (asRequested) granted else stringResource(R.string.audio_substituted, granted, requested),
        modifier = modifier
    )
}

@Composable
private fun HardwareBlock(hardware: AudioHardware, isResampled: Boolean, isFormatConverted: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.audio_row_hardware),
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier.padding(top = Spacing.Small)
    )
    if (hardware.sampleRate != null) {
        LabeledValue(
            label = stringResource(R.string.audio_row_sample_rate),
            value = stringResource(R.string.audio_hz, hardware.sampleRate),
            valueStyle = MonitorTypography.machineText
        )
    }
    if (hardware.channelCount != null) {
        LabeledValue(
            label = stringResource(R.string.audio_row_channels),
            value = hardware.channelCount.toString(),
            valueStyle = MonitorTypography.machineText
        )
    }
    if (hardware.format != null) {
        LabeledValue(
            label = stringResource(R.string.audio_row_format),
            value = hardware.format,
            valueStyle = MonitorTypography.machineText
        )
    }
    // A named gap rather than a blank: the hardware has a format, AAudio just has no constant for
    // it, and that is a different thing from the reading not having been taken.
    if (hardware.formatUnnameable) {
        Text(
            text = stringResource(R.string.audio_format_unnameable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (isResampled || isFormatConverted) {
        Row(
            modifier = Modifier.padding(top = Spacing.ExtraSmall),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            if (isResampled) MonitorTag(stringResource(R.string.audio_tag_resampled))
            if (isFormatConverted) MonitorTag(stringResource(R.string.audio_tag_converted))
        }
    }
}

@Composable
private fun AudioRequest.describe(): String = if (sampleRate != null) {
    stringResource(
        R.string.audio_requested_value_rate,
        stringResource(performanceMode.labelRes),
        stringResource(sharingMode.labelRes),
        sampleRate
    )
} else {
    stringResource(
        R.string.audio_requested_value,
        stringResource(performanceMode.labelRes),
        stringResource(sharingMode.labelRes)
    )
}

/** The probe labels the native side files its answers under. */
@get:StringRes
private val AudioProbe.labelRes: Int
    get() = when (label) {
        "low_latency_exclusive" -> R.string.audio_probe_low_latency_exclusive
        "low_latency_shared" -> R.string.audio_probe_low_latency_shared
        "default_shared" -> R.string.audio_probe_default_shared
        "resample_44100" -> R.string.audio_probe_resample
        else -> R.string.audio_probe_unknown
    }

@get:StringRes
private val AudioSharingMode.labelRes: Int
    get() = when (this) {
        AudioSharingMode.Exclusive -> R.string.audio_sharing_exclusive
        AudioSharingMode.Shared -> R.string.audio_sharing_shared
        AudioSharingMode.Unknown -> R.string.audio_mode_unknown
    }

@get:StringRes
private val AudioPerformanceMode.labelRes: Int
    get() = when (this) {
        AudioPerformanceMode.LowLatency -> R.string.audio_performance_low_latency
        AudioPerformanceMode.PowerSaving -> R.string.audio_performance_power_saving
        AudioPerformanceMode.PowerSavingOffloaded -> R.string.audio_performance_power_saving_offloaded
        AudioPerformanceMode.None -> R.string.audio_performance_none
        AudioPerformanceMode.Unknown -> R.string.audio_mode_unknown
    }

@MonitorPreviews
@Composable
private fun AudioPathPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        AudioPathContent(
            uiState = AudioPathUiState(
                audio = AudioPathSnapshot(
                    hardwareQueryAvailable = true,
                    probes = listOf(
                        AudioProbe(
                            label = "low_latency_exclusive",
                            requested = AudioRequest(AudioPerformanceMode.LowLatency, AudioSharingMode.Exclusive),
                            opened = true,
                            granted = AudioGranted(
                                performanceMode = AudioPerformanceMode.LowLatency,
                                sharingMode = AudioSharingMode.Shared,
                                format = "pcm_float",
                                sampleRate = 48000,
                                channelCount = 2,
                                framesPerBurst = 96,
                                bufferCapacity = 3840,
                                bufferSize = 192,
                                deviceId = 3
                            ),
                            hardware = AudioHardware(sampleRate = 48000, channelCount = 2, format = "pcm_i16")
                        ),
                        AudioProbe(
                            label = "resample_44100",
                            requested = AudioRequest(AudioPerformanceMode.LowLatency, AudioSharingMode.Shared, 44100),
                            opened = true,
                            granted = AudioGranted(
                                performanceMode = AudioPerformanceMode.LowLatency,
                                sharingMode = AudioSharingMode.Shared,
                                format = "pcm_float",
                                sampleRate = 44100,
                                channelCount = 2,
                                framesPerBurst = 96,
                                bufferCapacity = 3840,
                                bufferSize = 192,
                                deviceId = 3
                            ),
                            hardware = AudioHardware(sampleRate = 48000, channelCount = 2, format = "pcm_i16")
                        )
                    )
                ),
                hasLoaded = true
            ),
            onNavigateBack = {},
            onRefresh = {}
        )
    }
}
