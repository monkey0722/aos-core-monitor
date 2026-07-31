package com.aoscoremonitor.diagnostics

import android.hardware.Sensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Runs off-device: the `Sensor.TYPE_*` constants are compile-time integers, so the grouping and the
 * units can be checked without the platform behind them. Everything else about a [Sensor] needs a
 * device to answer, which is why neither of these reads one.
 */
class SensorCategoryTest {

    @Test
    fun motionSensorsGroupTogether() {
        val motion = listOf(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_GRAVITY,
            Sensor.TYPE_LINEAR_ACCELERATION,
            Sensor.TYPE_STEP_COUNTER
        )

        motion.forEach { type ->
            assertEquals("type $type", SensorCategory.Motion, sensorCategory(type))
        }
    }

    @Test
    fun theCompassAndTheProximitySensorAreAboutPosition() {
        assertEquals(SensorCategory.Position, sensorCategory(Sensor.TYPE_MAGNETIC_FIELD))
        assertEquals(SensorCategory.Position, sensorCategory(Sensor.TYPE_PROXIMITY))
        assertEquals(SensorCategory.Position, sensorCategory(Sensor.TYPE_ROTATION_VECTOR))
    }

    @Test
    fun theEnvironmentSensorsGroupTogether() {
        assertEquals(SensorCategory.Environment, sensorCategory(Sensor.TYPE_LIGHT))
        assertEquals(SensorCategory.Environment, sensorCategory(Sensor.TYPE_PRESSURE))
        assertEquals(SensorCategory.Environment, sensorCategory(Sensor.TYPE_AMBIENT_TEMPERATURE))
        assertEquals(SensorCategory.Environment, sensorCategory(Sensor.TYPE_RELATIVE_HUMIDITY))
    }

    @Test
    fun aVendorTypeIsListedRatherThanDropped() {
        // Types above the documented range are the vendor's own. The screen groups by this
        // function, so a type it does not know has to land somewhere rather than nowhere.
        assertEquals(SensorCategory.Other, sensorCategory(65_536))
        assertEquals(SensorCategory.Other, sensorCategory(Sensor.TYPE_HEART_RATE))
    }

    @Test
    fun eachSensorTypeReportsItsOwnUnit() {
        assertEquals(SensorUnit.MetresPerSecondSquared, sensorUnit(Sensor.TYPE_ACCELEROMETER))
        assertEquals(SensorUnit.RadiansPerSecond, sensorUnit(Sensor.TYPE_GYROSCOPE))
        assertEquals(SensorUnit.MicroTesla, sensorUnit(Sensor.TYPE_MAGNETIC_FIELD))
        assertEquals(SensorUnit.Lux, sensorUnit(Sensor.TYPE_LIGHT))
        assertEquals(SensorUnit.HectoPascal, sensorUnit(Sensor.TYPE_PRESSURE))
    }

    @Test
    fun aRotationVectorHasNoUnitToName() {
        // Its values are components of a unit quaternion. Labelling them with anything would be
        // making something up, which is the failure mode this app has been pulling out elsewhere.
        assertNull(sensorUnit(Sensor.TYPE_ROTATION_VECTOR))
        assertNull(sensorUnit(Sensor.TYPE_GAME_ROTATION_VECTOR))
        assertNull(sensorUnit(65_536))
    }

    @Test
    fun theFastestRateIsDerivedFromTheMinimumDelay() {
        val continuous = sensorInfo(minDelayUs = 5_000)
        val onChange = sensorInfo(minDelayUs = 0)

        assertEquals(200f, continuous.maxRateHz)
        // 0 means "reports only when the value changes", which is not a rate of any kind.
        assertNull(onChange.maxRateHz)
    }

    private fun sensorInfo(minDelayUs: Int) = SensorInfo(
        id = "1:test",
        name = "test",
        vendor = "test",
        type = Sensor.TYPE_ACCELEROMETER,
        stringType = "android.sensor.accelerometer",
        version = 1,
        maximumRange = 1f,
        resolution = 1f,
        power = 1f,
        minDelayUs = minDelayUs,
        maxDelayUs = 0,
        isWakeUp = false,
        isDynamic = false,
        reportingMode = 0
    )
}
