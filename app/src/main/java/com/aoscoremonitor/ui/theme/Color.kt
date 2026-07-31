package com.aoscoremonitor.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Fallback palette used when dynamic color is unavailable or switched off.
 *
 * The tones come from a blue seed, which reads as "instrumentation" rather than the stock
 * Material purple and keeps the reading cards neutral enough that the status colors below stay
 * the only saturated thing on screen.
 */

internal val PrimaryLight = Color(0xFF37618E)
internal val OnPrimaryLight = Color(0xFFFFFFFF)
internal val PrimaryContainerLight = Color(0xFFD2E4FF)
internal val OnPrimaryContainerLight = Color(0xFF001D36)
internal val SecondaryLight = Color(0xFF535F70)
internal val OnSecondaryLight = Color(0xFFFFFFFF)
internal val SecondaryContainerLight = Color(0xFFD7E3F8)
internal val OnSecondaryContainerLight = Color(0xFF101C2B)
internal val TertiaryLight = Color(0xFF6B5778)
internal val OnTertiaryLight = Color(0xFFFFFFFF)
internal val TertiaryContainerLight = Color(0xFFF2DAFF)
internal val OnTertiaryContainerLight = Color(0xFF251431)
internal val ErrorLight = Color(0xFFBA1A1A)
internal val OnErrorLight = Color(0xFFFFFFFF)
internal val ErrorContainerLight = Color(0xFFFFDAD6)
internal val OnErrorContainerLight = Color(0xFF410002)
internal val SurfaceLight = Color(0xFFF8F9FF)
internal val OnSurfaceLight = Color(0xFF191C20)
internal val SurfaceVariantLight = Color(0xFFDFE2EB)
internal val OnSurfaceVariantLight = Color(0xFF43474E)
internal val OutlineLight = Color(0xFF73777F)
internal val OutlineVariantLight = Color(0xFFC3C6CF)

internal val PrimaryDark = Color(0xFFA1C9FD)
internal val OnPrimaryDark = Color(0xFF00325A)
internal val PrimaryContainerDark = Color(0xFF1D4975)
internal val OnPrimaryContainerDark = Color(0xFFD2E4FF)
internal val SecondaryDark = Color(0xFFBBC7DB)
internal val OnSecondaryDark = Color(0xFF253140)
internal val SecondaryContainerDark = Color(0xFF3B4858)
internal val OnSecondaryContainerDark = Color(0xFFD7E3F8)
internal val TertiaryDark = Color(0xFFD7BDE4)
internal val OnTertiaryDark = Color(0xFF3B2947)
internal val TertiaryContainerDark = Color(0xFF523F5F)
internal val OnTertiaryContainerDark = Color(0xFFF2DAFF)
internal val ErrorDark = Color(0xFFFFB4AB)
internal val OnErrorDark = Color(0xFF690005)
internal val ErrorContainerDark = Color(0xFF93000A)
internal val OnErrorContainerDark = Color(0xFFFFDAD6)
internal val SurfaceDark = Color(0xFF111318)
internal val OnSurfaceDark = Color(0xFFE1E2E8)
internal val SurfaceVariantDark = Color(0xFF43474E)
internal val OnSurfaceVariantDark = Color(0xFFC3C6CF)
internal val OutlineDark = Color(0xFF8D9199)
internal val OutlineVariantDark = Color(0xFF43474E)

/*
 * Status colors, deliberately outside the Material color scheme.
 *
 * "Healthy" has to stay green and "degraded" has to stay amber even when dynamic color repaints
 * everything else from the wallpaper, so these are fixed rather than derived.
 */

internal val OkLight = Color(0xFF2E6B3F)
internal val OkContainerLight = Color(0xFFB4F0BF)
internal val OnOkContainerLight = Color(0xFF00210C)
internal val WarningLight = Color(0xFF7A5900)
internal val WarningContainerLight = Color(0xFFFFDF9A)
internal val OnWarningContainerLight = Color(0xFF261A00)

internal val OkDark = Color(0xFF98D8A6)
internal val OkContainerDark = Color(0xFF1E5030)
internal val OnOkContainerDark = Color(0xFFB4F0BF)
internal val WarningDark = Color(0xFFF2C048)
internal val WarningContainerDark = Color(0xFF5C4200)
internal val OnWarningContainerDark = Color(0xFFFFDF9A)
