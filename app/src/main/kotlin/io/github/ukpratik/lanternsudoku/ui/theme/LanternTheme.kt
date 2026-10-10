// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Display and UI families. M2 uses the system serif and sans as stand-ins; M5 bundles the subset Fraunces and Figtree
 * TTFs in `res/font/` (design-system §3.1). Fonts are never downloaded.
 */
val DisplayFamily: FontFamily = FontFamily.Serif
val UiFamily: FontFamily = FontFamily.SansSerif

/** Design-system §3 type scale mapped onto M3 roles. */
val LanternTypography = Typography(
    displaySmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 33.sp),
    headlineSmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 29.sp),
    titleLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 0.08.em),
)

/** Tabular figures for the timer and counts (design §3); kept as a style so M5's Figtree gets `tnum` for free. */
val TimerStyle = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, fontFeatureSettings = "tnum")

/** Game-bar level title (design §3 "game title"). */
val GameTitleStyle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)

/**
 * Picks the palette. M2: always [Palettes.Light]; M5 adds `themeMode`/`highContrast` selection (US-020) without changing
 * callers. No dynamic colour, ever.
 */
@Composable
fun LanternTheme(palette: LanternPalette = Palettes.Light, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLanternColors provides palette.lantern) {
        MaterialTheme(colorScheme = palette.colorScheme, typography = LanternTypography, content = content)
    }
}
