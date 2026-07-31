package com.aoscoremonitor.diagnostics.jni

import android.util.Log

/**
 * Whether libsystem_monitor loaded. Every `external fun` in this package throws without it.
 *
 * The load failure was already being caught and logged, but nothing recorded the result, so the
 * `external fun` calls went ahead anyway and threw [UnsatisfiedLinkError]. That is an [Error], not
 * an [Exception], so the `catch (e: Exception)` around each call did not stop it and the app
 * crashed — on a device where the library will not load, the native screens were unreachable. The
 * fallbacks those catch blocks guard were written for exactly this case; they just never ran.
 *
 * It lives here rather than on one collector because five of them now share the library: a
 * `loadLibrary` call per collector would ask the runtime to load the same object five times and
 * would report the failure five times over.
 */
internal object NativeLibrary {
    private const val TAG = "NativeLibrary"

    val isAvailable: Boolean = try {
        System.loadLibrary("system_monitor")
        Log.i(TAG, "Native library loaded successfully")
        true
    } catch (e: UnsatisfiedLinkError) {
        Log.e(TAG, "Failed to load native library; native readings are unavailable", e)
        false
    }
}
