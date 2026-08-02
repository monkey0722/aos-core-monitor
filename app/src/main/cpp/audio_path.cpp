// Three of the readings below reached the NDK in API 34 and this app's minSdk is 33. Without this,
// the NDK headers mark them `strict`, which hides them from a minSdk-33 build outright — a
// `__builtin_available` guard does not help, and the build fails with "is unavailable: introduced
// in Android 34". Defined it becomes a weak reference that the guard is then required to check.
//
// Scoped to this file rather than the whole library, and placed before the first include because
// that is where it is read: nothing else here calls an API newer than minSdk, and a library-wide
// definition would let the next file that does so link a weak symbol without anyone noticing.
#define __ANDROID_UNAVAILABLE_SYMBOLS_ARE_WEAK__

#include <aaudio/AAudio.h>
#include <jni.h>

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>

#include "native_util.h"

namespace {

using aoscm::JsonWriter;

/** Deletes the builder however the scope is left, exception included. */
using BuilderHandle =
        std::unique_ptr<AAudioStreamBuilder, decltype([](AAudioStreamBuilder* builder) {
                            AAudioStreamBuilder_delete(builder);
                        })>;

/** Closes the stream the same way. Never started, so closing is all there is to undo. */
using StreamHandle =
        std::unique_ptr<AAudioStream,
                        decltype([](AAudioStream* stream) { AAudioStream_close(stream); })>;

/** One configuration to ask for, and the name the screen files the answer under. */
struct Request {
    const char* label;
    aaudio_performance_mode_t performance_mode;
    aaudio_sharing_mode_t sharing_mode;
    /** AAUDIO_UNSPECIFIED leaves the rate to the system, which is the usual way to open a stream.
     */
    int32_t sample_rate;
};

/**
 * What to ask for, chosen so that the answers differ.
 *
 * A single stream would show what this device gave once and nothing about why. These four ask for
 * progressively less, so the point where the system stops saying yes is visible: whether the
 * exclusive MMAP path is on offer at all, whether low latency survives without it, and what the
 * ordinary path costs in buffering by comparison.
 */
constexpr Request kProbes[] = {
        {"low_latency_exclusive", AAUDIO_PERFORMANCE_MODE_LOW_LATENCY,
         AAUDIO_SHARING_MODE_EXCLUSIVE, AAUDIO_UNSPECIFIED},
        {"low_latency_shared", AAUDIO_PERFORMANCE_MODE_LOW_LATENCY, AAUDIO_SHARING_MODE_SHARED,
         AAUDIO_UNSPECIFIED},
        {"default_shared", AAUDIO_PERFORMANCE_MODE_NONE, AAUDIO_SHARING_MODE_SHARED,
         AAUDIO_UNSPECIFIED},
        // Asking for a rate the hardware does not run at is the only way to see the converter that
        // gets inserted when it happens: the stream reports 44100 and the hardware reports its own
        // rate, and the gap between the two is the resampler.
        {"resample_44100", AAUDIO_PERFORMANCE_MODE_LOW_LATENCY, AAUDIO_SHARING_MODE_SHARED, 44100},
};

std::string_view PerformanceModeName(aaudio_performance_mode_t mode) {
    switch (mode) {
        case AAUDIO_PERFORMANCE_MODE_LOW_LATENCY:
            return "low_latency";
        case AAUDIO_PERFORMANCE_MODE_POWER_SAVING:
            return "power_saving";
        case AAUDIO_PERFORMANCE_MODE_POWER_SAVING_OFFLOADED:
            return "power_saving_offloaded";
        case AAUDIO_PERFORMANCE_MODE_NONE:
            return "none";
        default:
            return "unknown";
    }
}

std::string_view SharingModeName(aaudio_sharing_mode_t mode) {
    switch (mode) {
        case AAUDIO_SHARING_MODE_EXCLUSIVE:
            return "exclusive";
        case AAUDIO_SHARING_MODE_SHARED:
            return "shared";
        default:
            return "unknown";
    }
}

std::string_view FormatName(aaudio_format_t format) {
    switch (format) {
        case AAUDIO_FORMAT_PCM_I16:
            return "pcm_i16";
        case AAUDIO_FORMAT_PCM_FLOAT:
            return "pcm_float";
        case AAUDIO_FORMAT_PCM_I24_PACKED:
            return "pcm_i24_packed";
        case AAUDIO_FORMAT_PCM_I32:
            return "pcm_i32";
        case AAUDIO_FORMAT_IEC61937:
            return "iec61937";
        case AAUDIO_FORMAT_MP3:
            return "mp3";
        case AAUDIO_FORMAT_AAC_LC:
            return "aac_lc";
        case AAUDIO_FORMAT_AAC_HE_V1:
            return "aac_he_v1";
        case AAUDIO_FORMAT_AAC_HE_V2:
            return "aac_he_v2";
        case AAUDIO_FORMAT_AAC_ELD:
            return "aac_eld";
        case AAUDIO_FORMAT_AAC_XHE:
            return "aac_xhe";
        case AAUDIO_FORMAT_OPUS:
            return "opus";
        default:
            // A format this app has no name for. It is still a format, so a stream carrying a
            // named one really is being converted; only the name is missing.
            return "unknown";
    }
}

/**
 * Writes a format, or nothing where the answer was not one.
 *
 * `AAudioStream_getHardwareFormat` is documented to answer AAUDIO_FORMAT_INVALID when the hardware
 * runs something AAudio has no name for, and UNSPECIFIED is not a format either. Published as a
 * token, either would compare unequal to the stream's own format and the screen would report a
 * conversion nobody observed — the one thing this app must not do. Absent instead, like every
 * other reading it could not take.
 */
void FieldFormatIfNamed(JsonWriter* writer, std::string_view key, aaudio_format_t format) {
    if (format != AAUDIO_FORMAT_INVALID && format != AAUDIO_FORMAT_UNSPECIFIED) {
        writer->Field(key, FormatName(format));
    }
}

/** Writes a count the stream reports, leaving the key out where it reported none. */
void FieldIfPositive(JsonWriter* writer, std::string_view key, int32_t value) {
    if (value > 0) {
        writer->Field(key, static_cast<int64_t>(value));
    }
}

/**
 * What the hardware behind the stream is actually running at.
 *
 * These three reached the NDK in API 34 and this app's minSdk is 33, so the NDK marks them
 * available only from there — `-Werror` turns an unguarded call into a build failure rather than
 * into a crash on a 33 device. Below 34 the keys are simply absent, which is how every other
 * unavailable reading in this app crosses to Kotlin.
 *
 * The reading is worth the guard: it is the only way to see that a stream reporting 48 kHz is
 * being fed to hardware running at something else, and nothing in the Java API reports it at all.
 */
void WriteHardware(JsonWriter* writer, AAudioStream* stream) {
    if (__builtin_available(android 34, *)) {
        writer->Key("hardware").BeginObject();
        FieldIfPositive(writer, "sample_rate", AAudioStream_getHardwareSampleRate(stream));
        FieldIfPositive(writer, "channel_count", AAudioStream_getHardwareChannelCount(stream));

        const aaudio_format_t format = AAudioStream_getHardwareFormat(stream);
        FieldFormatIfNamed(writer, "format", format);
        // Kept apart from a reading that was simply not taken: the hardware does have a format, and
        // AAudio saying it cannot name it is itself a fact about this device. The screen says so
        // rather than leaving a gap that reads as "nobody looked".
        if (format == AAUDIO_FORMAT_INVALID) {
            writer->Field("format_unnameable", true);
        }
        writer->EndObject();
    }
}

void WriteGranted(JsonWriter* writer, AAudioStream* stream) {
    writer->Key("granted").BeginObject();
    writer->Field("performance_mode", PerformanceModeName(AAudioStream_getPerformanceMode(stream)));
    writer->Field("sharing_mode", SharingModeName(AAudioStream_getSharingMode(stream)));
    FieldFormatIfNamed(writer, "format", AAudioStream_getFormat(stream));
    FieldIfPositive(writer, "sample_rate", AAudioStream_getSampleRate(stream));
    FieldIfPositive(writer, "channel_count", AAudioStream_getChannelCount(stream));
    // The burst is the reading this screen exists for: AudioManager offers a device-wide
    // recommendation, and this is what the stream actually settled on.
    FieldIfPositive(writer, "frames_per_burst", AAudioStream_getFramesPerBurst(stream));
    FieldIfPositive(writer, "buffer_capacity", AAudioStream_getBufferCapacityInFrames(stream));
    FieldIfPositive(writer, "buffer_size", AAudioStream_getBufferSizeInFrames(stream));
    FieldIfPositive(writer, "device_id", AAudioStream_getDeviceId(stream));
    writer->EndObject();
}

void WriteProbe(JsonWriter* writer, const Request& request) {
    writer->BeginObject();
    writer->Field("label", request.label);

    writer->Key("requested").BeginObject();
    writer->Field("performance_mode", PerformanceModeName(request.performance_mode));
    writer->Field("sharing_mode", SharingModeName(request.sharing_mode));
    if (request.sample_rate != AAUDIO_UNSPECIFIED) {
        writer->Field("sample_rate", static_cast<int64_t>(request.sample_rate));
    }
    writer->EndObject();

    AAudioStreamBuilder* raw_builder = nullptr;
    const aaudio_result_t built = AAudio_createStreamBuilder(&raw_builder);
    const BuilderHandle builder(raw_builder);
    if (built != AAUDIO_OK || !builder) {
        writer->Field("open_ok", false);
        writer->Field("open_error", AAudio_convertResultToText(built));
        writer->EndObject();
        return;
    }

    // Output only. An input stream would need RECORD_AUDIO, and a screen that reads the device
    // should not be the reason an app holds the microphone permission.
    AAudioStreamBuilder_setDirection(builder.get(), AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setPerformanceMode(builder.get(), request.performance_mode);
    AAudioStreamBuilder_setSharingMode(builder.get(), request.sharing_mode);
    if (request.sample_rate != AAUDIO_UNSPECIFIED) {
        AAudioStreamBuilder_setSampleRate(builder.get(), request.sample_rate);
    }

    AAudioStream* raw_stream = nullptr;
    const aaudio_result_t opened = AAudioStreamBuilder_openStream(builder.get(), &raw_stream);
    const StreamHandle stream(raw_stream);
    if (opened != AAUDIO_OK || !stream) {
        // A refusal is a reading: an exclusive request that this device will not grant fails here
        // on some implementations rather than quietly coming back shared.
        writer->Field("open_ok", false);
        writer->Field("open_error", AAudio_convertResultToText(opened));
        writer->EndObject();
        return;
    }

    // The stream is opened and never started. AAudioStream_requestStart is what powers the path
    // and begins consuming the buffer; without it nothing is played, no audio focus is taken, and
    // every reading below is still answered. The cost of stopping here is getXRunCount and
    // getTimestamp, which mean nothing on a stream that never ran — this app reads, and playing
    // silence to collect two more numbers is not reading.
    writer->Field("open_ok", true);
    WriteGranted(writer, stream.get());
    WriteHardware(writer, stream.get());
    writer->EndObject();
}

std::string Collect() {
    JsonWriter writer;
    writer.BeginObject();

    // Said once at the top rather than inferred from three missing keys per probe: a device on
    // API 33 cannot take the hardware readings at all, which is a property of the device and not
    // of any one probe.
    bool hardware_available = false;
    if (__builtin_available(android 34, *)) {
        hardware_available = true;
    }
    writer.Field("hardware_query_available", hardware_available);

    writer.Key("probes").BeginArray();
    for (const Request& request : kProbes) {
        WriteProbe(&writer, request);
    }
    writer.EndArray();

    writer.EndObject();
    return writer.Take();
}

}  // namespace

/**
 * What this device's audio path grants, against what was asked of it.
 *
 * Three layers that need not agree, and the Java API reaches none of them. What was requested is
 * this collector's own doing; what the stream settled on is AAudio's answer, which
 * `AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER` only approximates with a device-wide
 * recommendation; and what the hardware runs at is reported by nothing in the framework at all.
 *
 * Four streams are opened and closed. None is started, so nothing is played and no audio focus is
 * taken.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_aoscoremonitor_diagnostics_jni_NativeAudioInspector_getAudioPathNative(
        JNIEnv* env, jobject /* this */) {
    return aoscm::ReturnJson(env, [] { return Collect(); });
}
