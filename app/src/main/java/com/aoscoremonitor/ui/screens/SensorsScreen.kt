package com.aoscoremonitor.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aoscoremonitor.R
import com.aoscoremonitor.diagnostics.SensorCategory
import com.aoscoremonitor.diagnostics.SensorInfo
import com.aoscoremonitor.diagnostics.SensorReading
import com.aoscoremonitor.diagnostics.SensorUnit
import com.aoscoremonitor.diagnostics.sensorCategory
import com.aoscoremonitor.diagnostics.sensorUnit
import com.aoscoremonitor.ui.components.FullScreenMessage
import com.aoscoremonitor.ui.components.LabeledValue
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.SectionHeader
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.MonitorTypography
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.SensorsUiState
import com.aoscoremonitor.ui.viewmodel.SensorsViewModel
import com.aoscoremonitor.ui.viewmodel.monitorViewModel

@Composable
fun SensorsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SensorsViewModel = monitorViewModel { SensorsViewModel(it) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SensorsContent(
        uiState = uiState,
        onSensorClick = viewModel::toggle,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
private fun SensorsContent(
    uiState: SensorsUiState,
    onSensorClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    MonitorScaffold(
        title = stringResource(R.string.sensors_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) { innerPadding ->
        if (uiState.sensors.isEmpty()) {
            FullScreenMessage(
                message = stringResource(if (uiState.hasLoaded) R.string.sensors_empty else R.string.sensors_loading),
                icon = Icons.Default.Sensors,
                supportingText = stringResource(R.string.sensors_empty_hint).takeIf { uiState.hasLoaded },
                modifier = Modifier.padding(innerPadding)
            )
            return@MonitorScaffold
        }

        // Grouped once here rather than filtered per section, so a type this app has not heard of
        // still appears — under Other — instead of being dropped by four filters that miss it.
        val grouped = uiState.sensors.groupBy { sensorCategory(it.type) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            item(key = "subtitle") {
                Text(
                    text = stringResource(R.string.sensors_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.Small)
                )
            }
            SensorCategory.entries.forEach { category ->
                val sensors = grouped[category].orEmpty()
                if (sensors.isNotEmpty()) {
                    categorySection(
                        category = category,
                        sensors = sensors,
                        uiState = uiState,
                        onSensorClick = onSensorClick
                    )
                }
            }
        }
    }
}

private fun LazyListScope.categorySection(
    category: SensorCategory,
    sensors: List<SensorInfo>,
    uiState: SensorsUiState,
    onSensorClick: (String) -> Unit
) {
    item(key = "${category.name}-header") {
        SectionHeader(title = stringResource(category.labelRes), icon = category.icon)
    }
    items(sensors, key = { sensor -> sensor.id }) { sensor ->
        SensorCard(
            sensor = sensor,
            reading = uiState.reading.takeIf { uiState.selectedId == sensor.id },
            selected = uiState.selectedId == sensor.id,
            onClick = { onSensorClick(sensor.id) }
        )
    }
}

/**
 * One sensor, with its live values while it is the open one.
 *
 * Only the open sensor is registered — see [SensorsViewModel] — which is why this is a card the
 * user opens rather than a list that streams everything at once.
 */
@Composable
private fun SensorCard(sensor: SensorInfo, reading: SensorReading?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sensor.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = Spacing.Small)
            )
            if (sensor.isWakeUp) SensorTag(stringResource(R.string.sensors_wakeup))
        }

        Text(
            text = sensor.vendor,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = sensor.stringType,
            style = MonitorTypography.machineText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val unit = sensorUnit(sensor.type)?.let { stringResource(it.labelRes) }
        LabeledValue(
            label = stringResource(R.string.sensors_resolution),
            value = formatValue(sensor.resolution, unit)
        )
        LabeledValue(
            label = stringResource(R.string.sensors_range),
            value = formatValue(sensor.maximumRange, unit)
        )
        LabeledValue(
            label = stringResource(R.string.sensors_power),
            value = stringResource(R.string.sensors_milliamps, sensor.power)
        )
        sensor.maxRateHz?.let {
            LabeledValue(label = stringResource(R.string.sensors_max_rate), value = stringResource(R.string.sensors_hertz, it))
        }

        if (selected) {
            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.Small))
            if (reading == null) {
                Text(
                    // An on-change sensor reports when it changes and not before: proximity can sit
                    // here for as long as nothing passes the phone.
                    text = stringResource(R.string.sensors_waiting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                reading.values.forEachIndexed { index, value ->
                    LabeledValue(
                        label = axisLabel(index, reading.values.size),
                        value = formatValue(value, unit),
                        valueStyle = MonitorTypography.machineText
                    )
                }
                Text(
                    text = stringResource(R.string.sensors_accuracy, stringResource(accuracyLabel(reading.accuracy))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = stringResource(R.string.sensors_tap_to_read),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.ExtraSmall)
            )
        }
    }
}

@Composable
private fun SensorTag(text: String, modifier: Modifier = Modifier) {
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

/** Three values from a sensor are axes; anything else is numbered, because it is not for us to say. */
@Composable
private fun axisLabel(index: Int, count: Int): String = when {
    count == 1 -> stringResource(R.string.sensors_value)
    count == 3 -> AXES[index]
    else -> stringResource(R.string.sensors_value_indexed, index)
}

@Composable
private fun formatValue(value: Float, unit: String?): String =
    if (unit == null) FORMAT.format(value) else stringResource(R.string.sensors_value_with_unit, FORMAT.format(value), unit)

private val SensorCategory.labelRes: Int
    @StringRes
    get() = when (this) {
        SensorCategory.Motion -> R.string.sensors_category_motion
        SensorCategory.Position -> R.string.sensors_category_position
        SensorCategory.Environment -> R.string.sensors_category_environment
        SensorCategory.Other -> R.string.sensors_category_other
    }

private val SensorCategory.icon: ImageVector
    get() = when (this) {
        SensorCategory.Motion -> Icons.Default.Vibration
        SensorCategory.Position -> Icons.Default.Explore
        SensorCategory.Environment -> Icons.Default.Air
        SensorCategory.Other -> Icons.Default.Sensors
    }

private val SensorUnit.labelRes: Int
    @StringRes
    get() = when (this) {
        SensorUnit.MetresPerSecondSquared -> R.string.sensors_unit_acceleration
        SensorUnit.RadiansPerSecond -> R.string.sensors_unit_angular_rate
        SensorUnit.MicroTesla -> R.string.sensors_unit_magnetic
        SensorUnit.Lux -> R.string.sensors_unit_lux
        SensorUnit.HectoPascal -> R.string.sensors_unit_pressure
        SensorUnit.Centimetres -> R.string.sensors_unit_centimetres
        SensorUnit.Percent -> R.string.sensors_unit_percent
        SensorUnit.Celsius -> R.string.sensors_unit_celsius
        SensorUnit.Degrees -> R.string.sensors_unit_degrees
        SensorUnit.Steps -> R.string.sensors_unit_steps
    }

@StringRes
private fun accuracyLabel(accuracy: Int): Int = when (accuracy) {
    android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> R.string.sensors_accuracy_high
    android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> R.string.sensors_accuracy_medium
    android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW -> R.string.sensors_accuracy_low
    android.hardware.SensorManager.SENSOR_STATUS_NO_CONTACT -> R.string.sensors_accuracy_no_contact
    else -> R.string.sensors_accuracy_unreliable
}

private val AXES = listOf("x", "y", "z")

/**
 * Three decimals for every reading.
 *
 * A gyroscope at rest reads in thousandths and a light sensor in hundreds, so a format that suits
 * both has to be fixed rather than significant-figure based.
 */
private val FORMAT = java.text.DecimalFormat("#,##0.###")

@Preview(name = "Sensors", showBackground = true)
@Preview(name = "Sensors (dark)", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SensorsPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        SensorsContent(
            uiState = SensorsUiState(
                sensors = listOf(
                    SensorInfo(
                        id = "1:LSM6DSO Accelerometer",
                        name = "LSM6DSO Accelerometer",
                        vendor = "STMicroelectronics",
                        type = 1,
                        stringType = "android.sensor.accelerometer",
                        version = 1,
                        maximumRange = 78.4532f,
                        resolution = 0.0023956f,
                        power = 0.17f,
                        minDelayUs = 2404,
                        isWakeUp = false,
                        reportingMode = 0
                    ),
                    SensorInfo(
                        id = "5:TSL2591 Light",
                        name = "TSL2591 Light",
                        vendor = "AMS",
                        type = 5,
                        stringType = "android.sensor.light",
                        version = 1,
                        maximumRange = 40000f,
                        resolution = 1f,
                        power = 0.75f,
                        minDelayUs = 0,
                        isWakeUp = true,
                        reportingMode = 1
                    )
                ),
                hasLoaded = true,
                selectedId = "1:LSM6DSO Accelerometer",
                reading = SensorReading(listOf(0.0316f, 9.7812f, 0.1435f), 3, 0)
            ),
            onSensorClick = {},
            onNavigateBack = {}
        )
    }
}
