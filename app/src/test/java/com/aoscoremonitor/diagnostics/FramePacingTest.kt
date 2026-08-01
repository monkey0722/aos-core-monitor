package com.aoscoremonitor.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FramePacingTest {

    @Test
    fun framesArrivingOnTimeMissNothing() {
        val pacing = fold(FramePacing(periodNanos = SIXTY_HZ), List(10) { SIXTY_HZ })

        assertEquals(10, pacing.intervals)
        assertEquals(0, pacing.droppedFrames)
        assertEquals(0f, pacing.jankFraction)
        assertEquals(listOf(1 to 10), pacing.distribution)
    }

    @Test
    fun aGapOfThreeFramesMissedTwo() {
        // Not "one janky frame": a gap that spans three slots delivered one frame and missed the
        // two the panel was ready to show in between.
        val pacing = fold(FramePacing(periodNanos = SIXTY_HZ), listOf(SIXTY_HZ, SIXTY_HZ * 3))

        assertEquals(2, pacing.droppedFrames)
        assertEquals(listOf(1 to 1, 3 to 1), pacing.distribution)
        assertEquals(3, pacing.worstSlots)
    }

    @Test
    fun ordinaryJitterIsNotReadAsAMiss() {
        // A vsync delivered 2 ms late is still that vsync. Rounding rather than truncating is what
        // keeps a panel that is keeping up from reading as one that misses every frame.
        val pacing = fold(FramePacing(periodNanos = SIXTY_HZ), List(5) { SIXTY_HZ + 2_000_000 })

        assertEquals(0, pacing.droppedFrames)
        assertEquals(listOf(1 to 5), pacing.distribution)
    }

    @Test
    fun aDoubleCallbackForOneVsyncIsDropped() {
        // Choreographer can deliver two callbacks for one vsync. Counted, the second would push the
        // measured rate above what the panel can display, which is a reading that cannot be true.
        val pacing = fold(FramePacing(periodNanos = SIXTY_HZ), listOf(SIXTY_HZ, 200_000, SIXTY_HZ))

        assertEquals(2, pacing.intervals)
        assertEquals(SIXTY_HZ * 2, pacing.elapsedNanos)
    }

    @Test
    fun theRateIsMeasuredRatherThanAssumed() {
        // Half the frames arrive: a 60 Hz panel serving 30 fps, which is the case the whole screen
        // exists to show.
        val pacing = fold(FramePacing(periodNanos = SIXTY_HZ), List(30) { SIXTY_HZ * 2 })

        assertEquals(30f, pacing.measuredFps!!, 0.5f)
        assertEquals(30, pacing.droppedFrames)
        assertEquals(1f, pacing.jankFraction)
    }

    @Test
    fun nothingIsCountedBeforeTheFirstGap() {
        val pacing = FramePacing(periodNanos = SIXTY_HZ)

        assertNull(pacing.measuredFps)
        assertNull(pacing.jankFraction)
        assertEquals(0, pacing.droppedFrames)
    }

    @Test
    fun gapsAreIgnoredUntilTheFrameLengthIsKnown() {
        // The period is zero until the display has been read. A slot of no width would make every
        // gap infinitely many frames.
        val pacing = fold(FramePacing(), listOf(SIXTY_HZ, SIXTY_HZ))

        assertEquals(0, pacing.intervals)
        assertEquals(0, pacing.droppedFrames)
    }

    @Test
    fun theWorstGapSurvivesLaterCalmFrames() {
        val pacing = fold(FramePacing(periodNanos = SIXTY_HZ), listOf(SIXTY_HZ * 5, SIXTY_HZ, SIXTY_HZ))

        assertEquals(SIXTY_HZ * 5, pacing.worstIntervalNanos)
        assertEquals(5, pacing.worstSlots)
    }

    private fun fold(initial: FramePacing, intervals: List<Long>): FramePacing = intervals.fold(initial) { pacing, interval ->
        pacing.plus(interval)
    }

    private companion object {
        /** One frame at 60 Hz, in nanoseconds. */
        const val SIXTY_HZ = 16_666_666L
    }
}
