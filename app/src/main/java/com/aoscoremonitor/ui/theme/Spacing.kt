package com.aoscoremonitor.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The spacing steps the app lays out on.
 *
 * Every screen was hand-picking padding values, so gutters and card insets drifted apart by a few
 * dp from screen to screen. Naming the steps keeps them in step.
 */
object Spacing {
    /** Gap between an icon and the text it labels. */
    val ExtraSmall = 4.dp

    /** Gap between lines within one card. */
    val Small = 8.dp

    /** Gap between sibling cards in a list, and between the rows of a card that is a list. */
    val Medium = 12.dp

    /** Screen gutter and card inset. */
    val Large = 16.dp

    /** Gap between sections of a screen. */
    val ExtraLarge = 24.dp
}
