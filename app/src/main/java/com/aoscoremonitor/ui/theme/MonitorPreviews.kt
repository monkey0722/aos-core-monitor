package com.aoscoremonitor.ui.theme

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Renders a preview in both themes.
 *
 * Every screen in this app was carrying the same two `@Preview` lines — one plain, one with
 * `uiMode` set to night — which is forty annotations saying one thing. This is the multipreview
 * annotation the Compose tooling documents for exactly that.
 *
 * Not `@PreviewLightDark`, which the library already provides: that one does not set
 * `showBackground`, so adopting it would render every preview on a transparent surface and change
 * what twenty screens look like in the IDE. This keeps the background the screens were written
 * against.
 *
 * The name of each rendering is just "Light" and "Dark" because the preview panel already labels
 * them with the composable they came from.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class MonitorPreviews
