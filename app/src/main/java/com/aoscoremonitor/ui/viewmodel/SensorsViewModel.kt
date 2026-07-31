package com.aoscoremonitor.ui.viewmodel

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.SensorInfo
import com.aoscoremonitor.diagnostics.SensorReading
import com.aoscoremonitor.diagnostics.readSensorInventory
import com.aoscoremonitor.diagnostics.sensorId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The device's sensors, and the live values of the one the user opened.
 *
 * [selectedId] rather than a set: one sensor is registered at a time. Registering all of them would
 * turn a screen that describes the hardware into one that drains the battery describing it, and the
 * power cost of each is on the card as a number for exactly that reason.
 */
data class SensorsUiState(
    val sensors: List<SensorInfo> = emptyList(),
    val hasLoaded: Boolean = false,
    val selectedId: String? = null,
    val reading: SensorReading? = null
)

class SensorsViewModel(context: Context) : ViewModel() {

    private val sensorManager = context.getSystemService(SensorManager::class.java)

    /** Read once: the sensor list does not change under a running process, dynamic sensors aside. */
    private val inventory = readSensorInventory(sensorManager)

    private val selectedId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SensorsUiState> = selectedId
        // flatMapLatest, so selecting a second sensor closes the first flow and with it unregisters
        // the listener. A merge would leave both running.
        .flatMapLatest { id ->
            val sensor = id?.let(::sensorFor)
            val readings = if (sensor == null) flowOf(null) else readingsOf(sensor)
            readings.map { reading ->
                SensorsUiState(sensors = inventory, hasLoaded = true, selectedId = id, reading = reading)
            }
        }
        .stateIn(viewModelScope, WhileScreenVisible, SensorsUiState(sensors = inventory, hasLoaded = true))

    /** Opens a sensor, or closes the open one when it is tapped again. */
    fun toggle(id: String) {
        selectedId.value = if (selectedId.value == id) null else id
    }

    private val sensorsById: Map<String, Sensor> =
        sensorManager?.getSensorList(Sensor.TYPE_ALL).orEmpty().associateBy(::sensorId)

    private fun sensorFor(id: String): Sensor? = sensorsById[id]

    /**
     * Values from one sensor, for as long as anyone collects them.
     *
     * The event's `values` array is owned and reused by the framework, so it is copied here rather
     * than passed on: held as-is, every reading in the UI would change under it on the next event.
     */
    private fun readingsOf(sensor: Sensor): Flow<SensorReading?> = callbackFlow {
        // Cleared first, so the card does not show the previous sensor's numbers under the new
        // sensor's name while waiting for the first event — which for an on-change sensor like
        // proximity can be a long wait.
        trySend(null)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(
                    SensorReading(
                        values = event.values.toList(),
                        accuracy = event.accuracy,
                        timestampNanos = event.timestamp
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = sensorManager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI) == true
        awaitClose { if (registered) sensorManager?.unregisterListener(listener) }
    }
        // An accelerometer at SENSOR_DELAY_UI produces events faster than the screen can draw them,
        // and only the newest is worth drawing. Dropping the rest is the intent, not a side effect
        // of a small buffer.
        .conflate()
}
