package com.aoscoremonitor.diagnostics

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * How well this app is being served frames.
 *
 * Built from the gaps between successive `Choreographer` callbacks. A gap is measured in frame
 * slots rather than in milliseconds, because a gap that means everything is fine on a 60 Hz panel —
 * 16.7 ms — means a frame was missed on a 120 Hz one. [periodNanos] is what a slot is worth, taken
 * from the display's current mode.
 *
 * Kept apart from the callback that feeds it so the arithmetic can be exercised without a display:
 * every field here follows from the sequence of gaps and nothing else.
 */
data class FramePacing(
    val periodNanos: Long = 0,
    val intervals: Int = 0,
    val elapsedNanos: Long = 0,
    val worstIntervalNanos: Long = 0,
    val histogram: Map<Int, Int> = emptyMap()
) {

    /**
     * Frames the app was late for.
     *
     * A gap of one slot delivered one frame; a gap of three delivered one and missed two. Summed
     * this way rather than counted as "janky gaps", because a single stall of half a second is not
     * the same event as one late frame.
     */
    val droppedFrames: Int get() = histogram.entries.sumOf { (slots, count) -> (slots - 1) * count }

    /** The share of gaps that were longer than one slot. */
    val jankFraction: Float?
        get() {
            if (intervals == 0) return null
            val late = histogram.entries.filter { (slots, _) -> slots > 1 }.sumOf { it.value }
            return late.toFloat() / intervals
        }

    /**
     * The rate frames actually arrived at.
     *
     * Measured rather than taken from the mode: a panel that reports 120 Hz still delivers 60 when
     * the app cannot keep up, and the difference between the two numbers is the whole point of the
     * screen.
     */
    val measuredFps: Float? get() = if (elapsedNanos <= 0) null else intervals * NANOS_PER_SECOND / elapsedNanos

    /** The worst gap, in slots, for a screen that wants to say how bad the worst stall was. */
    val worstSlots: Int get() = slotsFor(worstIntervalNanos)

    /** The histogram in slot order, so the bars do not reshuffle as counts change. */
    val distribution: List<Pair<Int, Int>> get() = histogram.entries.sortedBy { it.key }.map { it.key to it.value }

    /**
     * Folds one gap in.
     *
     * Gaps shorter than half a slot are dropped rather than counted: `Choreographer` can deliver
     * two callbacks for one vsync when a frame is scheduled while one is already pending, and
     * counting that as a frame would report a rate above what the panel can display.
     */
    fun plus(intervalNanos: Long): FramePacing {
        if (periodNanos <= 0 || intervalNanos * 2 < periodNanos) {
            return this
        }
        val slots = slotsFor(intervalNanos)
        return copy(
            intervals = intervals + 1,
            elapsedNanos = elapsedNanos + intervalNanos,
            worstIntervalNanos = max(worstIntervalNanos, intervalNanos),
            histogram = histogram + (slots to (histogram[slots] ?: 0) + 1)
        )
    }

    /** How many frame slots a gap spans, rounded, so that ordinary jitter is not read as a miss. */
    private fun slotsFor(intervalNanos: Long): Int {
        if (periodNanos <= 0) return 0
        return max(1, (intervalNanos.toDouble() / periodNanos).roundToInt())
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000f
    }
}
