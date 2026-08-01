package com.aoscoremonitor.diagnostics

import android.os.Build

/**
 * What this device and this build of Android call themselves.
 *
 * The one thing the app had nowhere to say. Nineteen screens report what the system is doing and
 * none of them reported what the system *is*: the model, the release it runs, the patch level it
 * claims. The system info screen was four live readings and two thirds of a blank screen, and this
 * is what belongs above them.
 *
 * Not the kernel release — that is a different fact about a different layer, and the CPU screen
 * already reads it from `uname`.
 */
data class DeviceIdentity(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val securityPatch: String,
    val buildId: String,
    val fingerprint: String
) {
    /** "Google Pixel 8", without repeating the maker when the model already names it. */
    val name: String
        get() = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
}

/**
 * Reads the build constants.
 *
 * Static for the life of the process — these are baked into the system image — so the screen reads
 * them once rather than polling for a model number that cannot change.
 */
fun readDeviceIdentity(): DeviceIdentity = DeviceIdentity(
    manufacturer = Build.MANUFACTURER.orEmpty(),
    model = Build.MODEL.orEmpty(),
    androidRelease = Build.VERSION.RELEASE.orEmpty(),
    sdkInt = Build.VERSION.SDK_INT,
    securityPatch = Build.VERSION.SECURITY_PATCH.orEmpty(),
    buildId = Build.ID.orEmpty(),
    fingerprint = Build.FINGERPRINT.orEmpty()
)
