package com.aoscoremonitor.diagnostics

import android.hardware.Sensor
import android.hardware.SensorManager

/**
 * One sensor the device reports, with what it says about itself.
 *
 * Everything here is metadata the platform publishes without a permission and without the sensor
 * being switched on — the HAL's own description of the part. It is the one hardware inventory in
 * this app that is measured rather than illustrated: the HAL screen's interface list comes from
 * `lshal`, which an app may not run, and falls back to samples.
 */
data class SensorInfo(
    val id: String,
    val name: String,
    val vendor: String,
    val type: Int,
    val stringType: String,
    val version: Int,
    val maximumRange: Float,
    val resolution: Float,
    val power: Float,
    val minDelayUs: Int,
    val maxDelayUs: Int,
    val isWakeUp: Boolean,
    val isDynamic: Boolean,
    val reportingMode: Int
) {
    /** The fastest rate the sensor will report at, or null for one that reports only on change. */
    val maxRateHz: Float? get() = if (minDelayUs > 0) MICROS_PER_SECOND / minDelayUs else null

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000f
    }
}

/** How the sensors are grouped on screen — by what they measure, not by the order the HAL lists them. */
enum class SensorCategory {
    Motion,
    Position,
    Environment,
    Other
}

/** One reading, copied out of the event: the framework reuses the array it hands to the listener. */
data class SensorReading(val values: List<Float>, val accuracy: Int, val timestampNanos: Long)

/**
 * Everything the device exposes, in the order the screen shows it.
 *
 * `TYPE_ALL` includes the dynamic sensors and the vendor-defined ones above the documented range,
 * which is deliberate: what a device actually carries is the point of the screen.
 */
fun readSensorInventory(sensorManager: SensorManager?): List<SensorInfo> =
    sensorManager?.getSensorList(Sensor.TYPE_ALL).orEmpty().map { sensor ->
        SensorInfo(
            id = sensorId(sensor),
            name = sensor.name,
            vendor = sensor.vendor,
            type = sensor.type,
            stringType = sensor.stringType.orEmpty(),
            version = sensor.version,
            maximumRange = sensor.maximumRange,
            resolution = sensor.resolution,
            power = sensor.power,
            minDelayUs = sensor.minDelay,
            maxDelayUs = sensor.maxDelay,
            isWakeUp = sensor.isWakeUpSensor,
            isDynamic = sensor.isDynamicSensor,
            reportingMode = sensor.reportingMode
        )
    }

/**
 * A key for one sensor.
 *
 * Type and name together: a device may carry two sensors of the same type — an uncalibrated
 * magnetometer alongside a calibrated one — so neither is a key on its own. Defined here so that
 * the list and the view model that registers a listener agree on what identifies a row.
 */
fun sensorId(sensor: Sensor): String = "${sensor.type}:${sensor.name}"

/**
 * Which group a sensor belongs to.
 *
 * Kept as a function over the type constant rather than read from the sensor object so that it can
 * be exercised off-device: the constants are compile-time integers, while every method on [Sensor]
 * needs a device to answer.
 */
fun sensorCategory(type: Int): SensorCategory = when (type) {
    Sensor.TYPE_ACCELEROMETER,
    Sensor.TYPE_ACCELEROMETER_UNCALIBRATED,
    Sensor.TYPE_GRAVITY,
    Sensor.TYPE_GYROSCOPE,
    Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
    Sensor.TYPE_LINEAR_ACCELERATION,
    Sensor.TYPE_SIGNIFICANT_MOTION,
    Sensor.TYPE_STEP_COUNTER,
    Sensor.TYPE_STEP_DETECTOR -> SensorCategory.Motion

    Sensor.TYPE_GAME_ROTATION_VECTOR,
    Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR,
    Sensor.TYPE_MAGNETIC_FIELD,
    Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED,
    Sensor.TYPE_ORIENTATION,
    Sensor.TYPE_PROXIMITY,
    Sensor.TYPE_ROTATION_VECTOR -> SensorCategory.Position

    Sensor.TYPE_AMBIENT_TEMPERATURE,
    Sensor.TYPE_LIGHT,
    Sensor.TYPE_PRESSURE,
    Sensor.TYPE_RELATIVE_HUMIDITY,
    Sensor.TYPE_TEMPERATURE -> SensorCategory.Environment

    else -> SensorCategory.Other
}

/**
 * The unit a sensor's values are in, or null when the type has none to name.
 *
 * The rotation vectors are the reason for the null rather than a "unit" of some kind: their values
 * are components of a unit quaternion and are not in anything.
 */
fun sensorUnit(type: Int): SensorUnit? = when (type) {
    Sensor.TYPE_ACCELEROMETER,
    Sensor.TYPE_ACCELEROMETER_UNCALIBRATED,
    Sensor.TYPE_GRAVITY,
    Sensor.TYPE_LINEAR_ACCELERATION -> SensorUnit.MetresPerSecondSquared

    Sensor.TYPE_GYROSCOPE,
    Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> SensorUnit.RadiansPerSecond

    Sensor.TYPE_MAGNETIC_FIELD,
    Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> SensorUnit.MicroTesla

    Sensor.TYPE_LIGHT -> SensorUnit.Lux
    Sensor.TYPE_PRESSURE -> SensorUnit.HectoPascal
    Sensor.TYPE_PROXIMITY -> SensorUnit.Centimetres
    Sensor.TYPE_RELATIVE_HUMIDITY -> SensorUnit.Percent
    Sensor.TYPE_AMBIENT_TEMPERATURE, Sensor.TYPE_TEMPERATURE -> SensorUnit.Celsius
    Sensor.TYPE_ORIENTATION -> SensorUnit.Degrees
    Sensor.TYPE_STEP_COUNTER -> SensorUnit.Steps
    else -> null
}

/** The units the sensor types above report in. Named here, worded in strings.xml. */
enum class SensorUnit {
    MetresPerSecondSquared,
    RadiansPerSecond,
    MicroTesla,
    Lux,
    HectoPascal,
    Centimetres,
    Percent,
    Celsius,
    Degrees,
    Steps
}
