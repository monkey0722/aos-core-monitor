package com.aoscoremonitor.ui.viewmodel

import android.content.Context
import android.view.Choreographer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.DisplayInfo
import com.aoscoremonitor.diagnostics.FramePacing
import com.aoscoremonitor.diagnostics.displayChanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class FramePacingUiState(val display: DisplayInfo? = null, val pacing: FramePacing = FramePacing(), val hasLoaded: Boolean = false)

/**
 * Measures how well this app is being served frames.
 *
 * The measurement is of vsync callbacks arriving, not of drawing: a `Choreographer` callback that
 * re-posts itself is delivered on every vsync whether or not anything changed, so a gap longer than
 * one frame means the main thread was not there to take it. That is the same thing the system means
 * by a dropped frame, seen from inside the app.
 *
 * It measures the app it runs in, which includes this screen's own updates. That is not a flaw to
 * be worked around — it is the only frame pipeline an app can see — but it is why the screen says
 * so rather than presenting the number as the device's.
 *
 * Collection is bounded by [WhileScreenVisible] for a stronger reason than the other screens: a
 * self-reposting frame callback asks for a vsync forever, so leaving it running in the background
 * would hold the display pipeline awake for a reading nobody is looking at.
 */
class FramePacingViewModel(context: Context) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<FramePacingUiState> = displayChanges(context)
        // flatMapLatest, so a mode switch starts the measurement over. A slot is worth what the
        // current mode says it is worth, and a histogram half in 60 Hz slots and half in 120 Hz
        // ones counts two different things in one column.
        .flatMapLatest { display ->
            framePacing(display?.frameIntervalNanos ?: 0).map { pacing ->
                FramePacingUiState(display = display, pacing = pacing, hasLoaded = true)
            }
        }
        .stateIn(viewModelScope, WhileScreenVisible, FramePacingUiState())

    private fun framePacing(periodNanos: Long): Flow<FramePacing> = callbackFlow {
        var pacing = FramePacing(periodNanos = periodNanos)
        var previousFrameNanos = 0L
        val choreographer = Choreographer.getInstance()

        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                // Re-posted first, so a stall in the arithmetic below cannot cost the next frame.
                choreographer.postFrameCallback(this)
                if (previousFrameNanos != 0L) {
                    pacing = pacing.plus(frameTimeNanos - previousFrameNanos)
                    // Not every frame: at 120 Hz that would ask the screen to recompose 120 times a
                    // second to report on how well it is recomposing.
                    if (pacing.intervals % FRAMES_PER_EMIT == 0) {
                        trySend(pacing)
                    }
                }
                previousFrameNanos = frameTimeNanos
            }
        }

        // So the screen can say it is measuring before it has anything to report.
        trySend(pacing)
        choreographer.postFrameCallback(callback)
        awaitClose { choreographer.removeFrameCallback(callback) }
    }
        // Choreographer belongs to the thread it was asked for, and only the main thread's is
        // driven by the display.
        .flowOn(Dispatchers.Main)
        .conflate()

    private companion object {
        /** Four updates a second at 60 Hz, eight at 120. */
        const val FRAMES_PER_EMIT = 15
    }
}
