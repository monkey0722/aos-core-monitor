package com.aoscoremonitor.diagnostics.jni

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPathParsingTest {

    @Test
    fun readsARequestAndTheTwoAnswersToIt() {
        val snapshot = parseAudioPath(
            """
            {"hardware_query_available":true,"probes":[
              {"label":"low_latency_shared",
               "requested":{"performance_mode":"low_latency","sharing_mode":"shared"},
               "open_ok":true,
               "granted":{"performance_mode":"low_latency","sharing_mode":"shared",
                          "format":"pcm_float","sample_rate":48000,"channel_count":2,
                          "frames_per_burst":96,"buffer_capacity":3840,"buffer_size":192,
                          "device_id":3},
               "hardware":{"sample_rate":48000,"channel_count":2,"format":"pcm_i16"}}]}
            """.trimIndent()
        )

        assertTrue(snapshot.hardwareQueryAvailable)
        val probe = snapshot.probes.single()
        assertTrue(probe.opened)
        assertEquals(AudioPerformanceMode.LowLatency, probe.requested.performanceMode)
        assertEquals(96, probe.granted?.framesPerBurst)
        assertEquals(48000, probe.hardware?.sampleRate)
        assertTrue(probe.grantedAsRequested)
    }

    /**
     * An exclusive request that comes back shared is the reading, not a parse failure.
     *
     * This is the case the screen exists for: nothing in the Java API can say that the MMAP path
     * was asked for and refused, because nothing in it can ask.
     */
    @Test
    fun anExclusiveRequestGrantedAsSharedIsRecordedAsASubstitution() {
        val snapshot = parseAudioPath(
            """
            {"probes":[
              {"label":"low_latency_exclusive",
               "requested":{"performance_mode":"low_latency","sharing_mode":"exclusive"},
               "open_ok":true,
               "granted":{"performance_mode":"low_latency","sharing_mode":"shared",
                          "format":"pcm_float","sample_rate":48000}}]}
            """.trimIndent()
        )

        val probe = snapshot.probes.single()
        assertEquals(AudioSharingMode.Exclusive, probe.requested.sharingMode)
        assertEquals(AudioSharingMode.Shared, probe.granted?.sharingMode)
        assertFalse(probe.sharingModeAsRequested)
        assertTrue(probe.performanceModeAsRequested)
        assertFalse(probe.grantedAsRequested)
    }

    @Test
    fun aStreamRateThatDiffersFromTheHardwareIsAResampler() {
        val snapshot = parseAudioPath(
            """
            {"hardware_query_available":true,"probes":[
              {"label":"resample_44100",
               "requested":{"performance_mode":"low_latency","sharing_mode":"shared","sample_rate":44100},
               "open_ok":true,
               "granted":{"performance_mode":"low_latency","sharing_mode":"shared",
                          "format":"pcm_float","sample_rate":44100},
               "hardware":{"sample_rate":48000,"format":"pcm_i16"}}]}
            """.trimIndent()
        )

        val probe = snapshot.probes.single()
        assertTrue(probe.isResampled)
        assertTrue(probe.isFormatConverted)
        // The rate was granted exactly as asked. That it does not match the hardware is a separate
        // statement, and conflating the two would report the request as refused.
        assertTrue(probe.sampleRateAsRequested)
        assertTrue(probe.grantedAsRequested)
    }

    @Test
    fun aStreamThatMatchesItsHardwareIsNotResampled() {
        val snapshot = parseAudioPath(
            """
            {"hardware_query_available":true,"probes":[
              {"label":"default_shared","requested":{"performance_mode":"none","sharing_mode":"shared"},
               "open_ok":true,
               "granted":{"performance_mode":"none","sharing_mode":"shared","format":"pcm_i16",
                          "sample_rate":48000},
               "hardware":{"sample_rate":48000,"format":"pcm_i16"}}]}
            """.trimIndent()
        )

        val probe = snapshot.probes.single()
        assertFalse(probe.isResampled)
        assertFalse(probe.isFormatConverted)
    }

    /**
     * A format AAudio would not name is not evidence of a conversion.
     *
     * `AAudioStream_getHardwareFormat` answers `AAUDIO_FORMAT_INVALID` when the hardware runs
     * something the API has no constant for. Carried across as a token it would compare unequal to
     * the stream's own format and the screen would report a conversion nobody observed, which is
     * the one thing this app is built not to do. The native side leaves the key out instead and
     * says why separately.
     */
    @Test
    fun anUnnameableHardwareFormatIsNotReportedAsAConversion() {
        val snapshot = parseAudioPath(
            """
            {"hardware_query_available":true,"probes":[
              {"label":"default_shared","requested":{"performance_mode":"none","sharing_mode":"shared"},
               "open_ok":true,
               "granted":{"performance_mode":"none","sharing_mode":"shared","format":"pcm_float",
                          "sample_rate":48000},
               "hardware":{"sample_rate":48000,"format_unnameable":true}}]}
            """.trimIndent()
        )

        val probe = snapshot.probes.single()
        assertNull("An unnameable format must not arrive as a format", probe.hardware?.format)
        assertTrue(probe.hardware?.formatUnnameable == true)
        assertFalse("Claimed a conversion it did not observe", probe.isFormatConverted)
        // The rate was still read, and it matches, so nothing is claimed there either.
        assertFalse(probe.isResampled)
    }

    /** The same on the stream's own side: no format read means no comparison to make. */
    @Test
    fun aStreamWithoutAReadableFormatIsNotReportedAsAConversion() {
        val snapshot = parseAudioPath(
            """
            {"hardware_query_available":true,"probes":[
              {"label":"default_shared","requested":{"performance_mode":"none","sharing_mode":"shared"},
               "open_ok":true,
               "granted":{"performance_mode":"none","sharing_mode":"shared","sample_rate":48000},
               "hardware":{"sample_rate":48000,"format":"pcm_i16"}}]}
            """.trimIndent()
        )

        val probe = snapshot.probes.single()
        assertNull(probe.granted?.format)
        assertFalse(probe.isFormatConverted)
    }

    @Test
    fun aRefusedRequestCarriesTheReasonAndNoReadings() {
        val snapshot = parseAudioPath(
            """
            {"probes":[
              {"label":"low_latency_exclusive",
               "requested":{"performance_mode":"low_latency","sharing_mode":"exclusive"},
               "open_ok":false,"open_error":"AAUDIO_ERROR_INVALID_STATE"}]}
            """.trimIndent()
        )

        val probe = snapshot.probes.single()
        assertFalse(probe.opened)
        assertEquals("AAUDIO_ERROR_INVALID_STATE", probe.openError)
        assertNull(probe.granted)
        assertNull(probe.hardware)
        assertFalse(probe.grantedAsRequested)
        // Nothing to compare against, so a refusal must not read as a resampled path.
        assertFalse(probe.isResampled)
    }

    /**
     * A query that was put and answered nothing is not a query that was never put.
     *
     * The collector opens the hardware object whenever the device is new enough to be asked, and a
     * HAL that implements none of the three leaves it empty — which is what a legacy shared stream
     * does. The object arriving says the questions were asked; [AudioHardware.hasReadings] is what
     * the screen needs to avoid printing a heading with nothing under it.
     */
    @Test
    fun aHardwareObjectWithNoReadingsSaysTheQueriesWentUnanswered() {
        val snapshot = parseAudioPath(
            """
            {"hardware_query_available":true,"probes":[
              {"label":"default_shared","requested":{"performance_mode":"none","sharing_mode":"shared"},
               "open_ok":true,
               "granted":{"performance_mode":"none","sharing_mode":"shared","format":"pcm_float",
                          "sample_rate":48000},
               "hardware":{}}]}
            """.trimIndent()
        )

        val probe = snapshot.probes.single()
        assertNotNull("The query was put, so the object must survive the parse", probe.hardware)
        assertFalse("Nothing was answered, so there is nothing to show", probe.hardware!!.hasReadings)
        assertFalse(probe.isResampled)
        assertFalse(probe.isFormatConverted)
    }

    /** Below API 34 the hardware readings do not exist, which is not the same as matching. */
    @Test
    fun withoutTheHardwareQueryNothingIsClaimedAboutConversion() {
        val snapshot = parseAudioPath(
            """
            {"hardware_query_available":false,"probes":[
              {"label":"default_shared","requested":{"performance_mode":"none","sharing_mode":"shared"},
               "open_ok":true,
               "granted":{"performance_mode":"none","sharing_mode":"shared","format":"pcm_float",
                          "sample_rate":48000}}]}
            """.trimIndent()
        )

        val probe = snapshot.probes.single()
        assertFalse(snapshot.hardwareQueryAvailable)
        assertNull(probe.hardware)
        assertFalse(probe.isResampled)
        assertFalse(probe.isFormatConverted)
    }

    @Test
    fun aRateLeftToTheSystemIsGrantedByDefinition() {
        val snapshot = parseAudioPath(
            """
            {"probes":[
              {"label":"default_shared","requested":{"performance_mode":"none","sharing_mode":"shared"},
               "open_ok":true,
               "granted":{"performance_mode":"none","sharing_mode":"shared","format":"pcm_float",
                          "sample_rate":48000}}]}
            """.trimIndent()
        )

        val probe = snapshot.probes.single()
        assertNull(probe.requested.sampleRate)
        assertTrue(probe.sampleRateAsRequested)
    }

    @Test
    fun anUnnamedModeIsUnknownRatherThanAGuess() {
        val snapshot = parseAudioPath(
            """
            {"probes":[
              {"label":"x","requested":{"performance_mode":"turbo","sharing_mode":"borrowed"},
               "open_ok":false}]}
            """.trimIndent()
        )

        val probe = snapshot.probes.single()
        assertEquals(AudioPerformanceMode.Unknown, probe.requested.performanceMode)
        assertEquals(AudioSharingMode.Unknown, probe.requested.sharingMode)
    }
}
