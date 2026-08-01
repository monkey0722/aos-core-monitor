package com.aoscoremonitor.diagnostics

import android.content.Context
import android.hardware.display.DeviceProductInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Surface
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** What the panel is doing, which is more than on or off: a dozing screen is still displaying. */
enum class DisplayState {
    On,
    Off,

    /** Showing an always-on or ambient image at a low rate. */
    Doze,

    /** Dozing with the CPU no longer driving it — the panel is holding the last image itself. */
    DozeSuspended,

    /** On, with the display pipeline suspended. */
    OnSuspended,
    Unknown;

    internal companion object {
        fun of(state: Int): DisplayState = when (state) {
            Display.STATE_OFF -> Off
            Display.STATE_ON -> On
            Display.STATE_DOZE -> Doze
            Display.STATE_DOZE_SUSPEND -> DozeSuspended
            Display.STATE_ON_SUSPEND -> OnSuspended
            else -> Unknown
        }
    }
}

/**
 * A high dynamic range format the panel accepts.
 *
 * Kept as an enum rather than the platform's integer so the wording lives in strings.xml, the same
 * arrangement the thread and sensor screens use for their kernel and HAL constants.
 */
enum class HdrType {
    DolbyVision,
    Hdr10,
    Hlg,
    Hdr10Plus,
    Unknown;

    internal companion object {
        fun of(type: Int): HdrType = when (type) {
            HDR_DOLBY_VISION -> DolbyVision
            HDR_10 -> Hdr10
            HDR_HLG -> Hlg
            HDR_10_PLUS -> Hdr10Plus
            else -> Unknown
        }

        // The values of Display.HdrCapabilities.HDR_TYPE_*, which are constants of a class
        // deprecated in API 34 while the values they name are what both the old and the new API
        // return.
        const val HDR_DOLBY_VISION = 1
        const val HDR_10 = 2
        const val HDR_HLG = 3
        const val HDR_10_PLUS = 4
    }
}

/** How the panel is wired to the device. */
enum class DisplayConnection {
    /** The screen in the phone. */
    BuiltIn,

    /** Plugged straight in — HDMI, DisplayPort. */
    Direct,

    /** Reached through something else, as a display behind a receiver is. */
    Transitive,
    Unknown
}

/**
 * What the panel says it is, from the EDID the platform parsed.
 *
 * Absent on most phones: a built-in panel has no EDID to read, and the platform answers with null
 * rather than with blanks. Reported when it is there because it is the one place the device says
 * who made the screen.
 */
data class DisplayProduct(
    val manufacturerPnpId: String? = null,
    val productId: String? = null,
    val manufactureYear: Int? = null,
    val connection: DisplayConnection = DisplayConnection.Unknown
) {
    val hasIdentity: Boolean get() = manufacturerPnpId != null || productId != null || manufactureYear != null
}

/** One mode the panel can be driven in. */
data class DisplayMode(val id: Int, val widthPixels: Int, val heightPixels: Int, val refreshRateHz: Float) {
    /** What one frame is worth at this rate, which is what a dropped frame is measured against. */
    val frameIntervalNanos: Long get() = if (refreshRateHz > 0f) (NANOS_PER_SECOND / refreshRateHz).toLong() else 0

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000f
    }
}

/** The area a cutout takes out of the display, as the insets that keep content clear of it. */
data class CutoutInsets(val left: Int, val top: Int, val right: Int, val bottom: Int, val boundingRects: Int)

/**
 * The default display, as the platform describes it.
 *
 * Read through [DisplayManager] rather than through a window: the collectors here are handed the
 * application context, which has no window and no display of its own, and asking it for one throws.
 * The manager answers for the display itself, which is what this screen is about anyway.
 */
data class DisplayInfo(
    val name: String,
    val displayId: Int,
    val state: DisplayState,
    val rotationDegrees: Int,
    val currentMode: DisplayMode?,
    val modes: List<DisplayMode> = emptyList(),
    val densityDpi: Int = 0,
    val density: Float = 0f,
    val xDpi: Float = 0f,
    val yDpi: Float = 0f,
    val fontScale: Float = 0f,
    val hdrTypes: List<HdrType> = emptyList(),
    val isWideColorGamut: Boolean = false,
    val cutout: CutoutInsets? = null,
    val isSecure: Boolean = false,
    val isRound: Boolean = false,
    val supportsMinimalPostProcessing: Boolean = false,
    val appVsyncOffsetNanos: Long = 0,
    val presentationDeadlineNanos: Long = 0,
    val product: DisplayProduct = DisplayProduct()
) {
    /** The rate the panel is being driven at now, which the frame screen measures against. */
    val refreshRateHz: Float get() = currentMode?.refreshRateHz ?: 0f

    val frameIntervalNanos: Long get() = currentMode?.frameIntervalNanos ?: 0
}

/**
 * Reads the default display.
 *
 * Returns null only where there is no display service at all, which no device this app runs on is;
 * everything a particular panel does not report is left null inside the reading rather than
 * collapsing the whole of it.
 */
fun readDisplayInfo(context: Context): DisplayInfo? {
    val displayManager = context.getSystemService(DisplayManager::class.java) ?: return null
    val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
    val metrics = context.resources.displayMetrics
    val current = display.mode

    return DisplayInfo(
        name = display.name.orEmpty(),
        displayId = display.displayId,
        state = DisplayState.of(display.state),
        rotationDegrees = rotationDegrees(display.rotation),
        currentMode = current?.let(::toMode),
        modes = display.supportedModes.orEmpty().map(::toMode),
        densityDpi = metrics.densityDpi,
        density = metrics.density,
        xDpi = metrics.xdpi,
        yDpi = metrics.ydpi,
        fontScale = context.resources.configuration.fontScale,
        hdrTypes = readHdrTypes(display, current),
        isWideColorGamut = display.isWideColorGamut,
        cutout = display.cutout?.let { cutout ->
            CutoutInsets(
                left = cutout.safeInsetLeft,
                top = cutout.safeInsetTop,
                right = cutout.safeInsetRight,
                bottom = cutout.safeInsetBottom,
                boundingRects = cutout.boundingRects.size
            )
        },
        isSecure = display.flags and Display.FLAG_SECURE != 0,
        isRound = display.flags and Display.FLAG_ROUND != 0,
        supportsMinimalPostProcessing = display.isMinimalPostProcessingSupported,
        appVsyncOffsetNanos = display.appVsyncOffsetNanos,
        presentationDeadlineNanos = display.presentationDeadlineNanos,
        product = display.deviceProductInfo?.let(::toProduct) ?: DisplayProduct()
    )
}

/**
 * The display, and every change to it.
 *
 * A listener rather than a poll: rotation, a mode switch and the screen going to doze are events
 * the platform already publishes, and polling for them would either miss them or spend a wake-up
 * every second finding nothing changed.
 */
fun displayChanges(context: Context): Flow<DisplayInfo?> = callbackFlow {
    trySend(readDisplayInfo(context))

    val displayManager = context.getSystemService(DisplayManager::class.java)
    val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                trySend(readDisplayInfo(context))
            }
        }
    }

    // The main looper, not the collecting thread's: this flow is collected from wherever a view
    // model happens to be, and registerDisplayListener needs a handler for a thread that has one.
    displayManager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
    awaitClose { displayManager?.unregisterDisplayListener(listener) }
}
    // Several changes can land in one turn — a rotation reports the size and the mode separately —
    // and only the newest reading is worth drawing.
    .conflate()

private fun toMode(mode: Display.Mode) = DisplayMode(
    id = mode.modeId,
    widthPixels = mode.physicalWidth,
    heightPixels = mode.physicalHeight,
    refreshRateHz = mode.refreshRate
)

private fun toProduct(info: DeviceProductInfo) = DisplayProduct(
    manufacturerPnpId = info.manufacturerPnpId.takeIf { it.isNotEmpty() },
    productId = info.productId.takeIf { it.isNotEmpty() },
    manufactureYear = info.manufactureYear.takeIf { it > 0 },
    connection = when (info.connectionToSinkType) {
        DeviceProductInfo.CONNECTION_TO_SINK_BUILT_IN -> DisplayConnection.BuiltIn
        DeviceProductInfo.CONNECTION_TO_SINK_DIRECT -> DisplayConnection.Direct
        DeviceProductInfo.CONNECTION_TO_SINK_TRANSITIVE -> DisplayConnection.Transitive
        else -> DisplayConnection.Unknown
    }
)

/**
 * Which HDR formats the panel accepts.
 *
 * The list moved from the display to the mode in API 34, because a panel can accept HDR in one mode
 * and not in another, and the old call was deprecated rather than made to say which mode it meant.
 * Both are read here: the app supports devices on either side of that change.
 */
private fun readHdrTypes(display: Display, mode: Display.Mode?): List<HdrType> {
    val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && mode != null) {
        mode.supportedHdrTypes
    } else {
        @Suppress("DEPRECATION")
        display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
    }
    return types.map(HdrType::of).distinct()
}

private fun rotationDegrees(rotation: Int): Int = when (rotation) {
    Surface.ROTATION_90 -> 90
    Surface.ROTATION_180 -> 180
    Surface.ROTATION_270 -> 270
    else -> 0
}
