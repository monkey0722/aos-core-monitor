package com.aoscoremonitor.diagnostics

/**
 * A collected value together with whether it came from the device or is built-in sample data.
 *
 * Several collectors fall back to hard-coded examples when the real source is unavailable — a
 * restricted /proc path, a missing permission, or a helper binary that fails to run. Carrying the
 * distinction in the type keeps callers from presenting samples as if they were measurements.
 */
data class Collected<T>(val value: T, val isSample: Boolean) {
    companion object {
        fun <T> real(value: T) = Collected(value, isSample = false)

        fun <T> sample(value: T) = Collected(value, isSample = true)

        /**
         * Returns [collected] as real data, or [fallback] marked as sample data when
         * [collected] is empty.
         */
        fun <T> realOrSample(collected: List<T>, fallback: List<T>): Collected<List<T>> =
            if (collected.isEmpty()) sample(fallback) else real(collected)
    }
}
