package com.aoscoremonitor.diagnostics.jni

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Whether a stream has the device to itself.
 *
 * The Java API has no equivalent concept: an `AudioTrack` is always mixed. Exclusive is the MMAP
 * path, and asking for it is not the same as getting it.
 */
enum class AudioSharingMode {
    Exclusive,
    Shared,
    Unknown;

    internal companion object {
        fun of(token: String?): AudioSharingMode = when (token) {
            "exclusive" -> Exclusive
            "shared" -> Shared
            else -> Unknown
        }
    }
}

/** How hard the system was asked to work to keep the path short. */
enum class AudioPerformanceMode {
    LowLatency,
    PowerSaving,
    PowerSavingOffloaded,
    None,
    Unknown;

    internal companion object {
        fun of(token: String?): AudioPerformanceMode = when (token) {
            "low_latency" -> LowLatency
            "power_saving" -> PowerSaving
            "power_saving_offloaded" -> PowerSavingOffloaded
            "none" -> None
            else -> Unknown
        }
    }
}

/** What one probe asked the system for. */
data class AudioRequest(
    val performanceMode: AudioPerformanceMode,
    val sharingMode: AudioSharingMode,
    /** Null where the rate was left to the system, which is how a stream is usually opened. */
    val sampleRate: Int? = null
)

/**
 * What the stream settled on.
 *
 * @param framesPerBurst the reading this screen exists for. `AudioManager` offers
 *   `PROPERTY_OUTPUT_FRAMES_PER_BUFFER`, which is a device-wide recommendation; this is what this
 *   stream actually got.
 */
data class AudioGranted(
    val performanceMode: AudioPerformanceMode,
    val sharingMode: AudioSharingMode,
    /** Null where AAudio answered with something that is not a format. See [AudioHardware.format]. */
    val format: String? = null,
    val sampleRate: Int? = null,
    val channelCount: Int? = null,
    val framesPerBurst: Int? = null,
    val bufferCapacity: Int? = null,
    val bufferSize: Int? = null,
    val deviceId: Int? = null
)

/**
 * What the hardware under the stream runs at. Reported by nothing in the Java API.
 *
 * @param format null where AAudio would not name one, which is not the same as the hardware having
 *   no format. [formatUnnameable] tells the two apart.
 * @param formatUnnameable AAudio answered `AAUDIO_FORMAT_INVALID`: the hardware runs a format the
 *   API has no constant for. Whether anything is converted cannot be said from that, so nothing
 *   claims it is.
 */
data class AudioHardware(
    val sampleRate: Int? = null,
    val channelCount: Int? = null,
    val format: String? = null,
    val formatUnnameable: Boolean = false
)

/**
 * One request, and the two answers to it.
 *
 * @param openError AAudio's own name for the failure, as `AAudio_convertResultToText` gives it.
 *   Null when the stream opened.
 */
data class AudioProbe(
    val label: String,
    val requested: AudioRequest,
    val opened: Boolean,
    val openError: String? = null,
    val granted: AudioGranted? = null,
    val hardware: AudioHardware? = null
) {
    val sharingModeAsRequested: Boolean get() = granted?.sharingMode == requested.sharingMode

    val performanceModeAsRequested: Boolean get() = granted?.performanceMode == requested.performanceMode

    /** A rate left to the system is granted by definition; one that was named may not be. */
    val sampleRateAsRequested: Boolean
        get() = requested.sampleRate == null || granted?.sampleRate == requested.sampleRate

    /** Nothing was substituted. */
    val grantedAsRequested: Boolean
        get() = opened && sharingModeAsRequested && performanceModeAsRequested && sampleRateAsRequested

    /**
     * The stream runs at one rate and the hardware at another, so something converts between them.
     *
     * This is the whole reason the fourth probe asks for a rate the hardware is unlikely to have:
     * a resampler is invisible from the Java side, and the gap between these two numbers is it.
     */
    val isResampled: Boolean
        get() {
            val stream = granted?.sampleRate ?: return false
            val hardware = hardware?.sampleRate ?: return false
            return stream != hardware
        }

    /** The same, for a sample format the hardware does not take directly. */
    val isFormatConverted: Boolean
        get() {
            val stream = granted?.format ?: return false
            val hardware = hardware?.format ?: return false
            return stream != hardware
        }
}

/**
 * The probes, and whether this device can answer for its hardware at all.
 *
 * @param hardwareQueryAvailable false below API 34, where the three hardware readings do not exist.
 *   Carried once rather than inferred from missing keys on each probe: it is a property of the
 *   device, not of any one request.
 */
data class AudioPathSnapshot(val hardwareQueryAvailable: Boolean = false, val probes: List<AudioProbe> = emptyList())

/**
 * Asks the audio system for several paths and reports what it granted.
 *
 * Four output streams are opened and closed. None is started, so nothing is played and no audio
 * focus is taken — which is also why there is no XRun count here: it means nothing on a stream
 * that never ran.
 */
class NativeAudioInspector {

    external fun getAudioPathNative(): String

    suspend fun read(): AudioPathSnapshot? = withContext(Dispatchers.IO) {
        if (!NativeLibrary.isAvailable) return@withContext null
        try {
            parseAudioPath(getAudioPathNative())
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing the audio path", e)
            null
        }
    }

    private companion object {
        const val TAG = "NativeAudioInspector"
    }
}

internal fun parseAudioPath(json: String): AudioPathSnapshot {
    val root = JSONObject(json)

    val probes = root.optJSONArray("probes")?.mapObjects { probe ->
        val requested = probe.optJSONObject("requested")
        val granted = probe.optJSONObject("granted")
        val hardware = probe.optJSONObject("hardware")

        AudioProbe(
            label = probe.optString("label"),
            requested = AudioRequest(
                performanceMode = AudioPerformanceMode.of(requested?.stringOrNull("performance_mode")),
                sharingMode = AudioSharingMode.of(requested?.stringOrNull("sharing_mode")),
                sampleRate = requested?.intOrNull("sample_rate")
            ),
            opened = probe.optBoolean("open_ok"),
            openError = probe.stringOrNull("open_error"),
            granted = granted?.let {
                AudioGranted(
                    performanceMode = AudioPerformanceMode.of(it.stringOrNull("performance_mode")),
                    sharingMode = AudioSharingMode.of(it.stringOrNull("sharing_mode")),
                    format = it.stringOrNull("format"),
                    sampleRate = it.intOrNull("sample_rate"),
                    channelCount = it.intOrNull("channel_count"),
                    framesPerBurst = it.intOrNull("frames_per_burst"),
                    bufferCapacity = it.intOrNull("buffer_capacity"),
                    bufferSize = it.intOrNull("buffer_size"),
                    deviceId = it.intOrNull("device_id")
                )
            },
            hardware = hardware?.let {
                AudioHardware(
                    sampleRate = it.intOrNull("sample_rate"),
                    channelCount = it.intOrNull("channel_count"),
                    format = it.stringOrNull("format"),
                    formatUnnameable = it.optBoolean("format_unnameable")
                )
            }
        )
    }.orEmpty()

    return AudioPathSnapshot(
        hardwareQueryAvailable = root.optBoolean("hardware_query_available"),
        probes = probes
    )
}
