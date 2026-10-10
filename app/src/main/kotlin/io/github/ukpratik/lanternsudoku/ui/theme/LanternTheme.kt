// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.data.ThemeMode

/**
 * Display and UI families: Latin-subset Fraunces 500/600 and Figtree 400/500/700 bundled in `res/font/`
 * (design-system §3.1). Fonts are never downloaded; glyphs outside the subset fall back to the system font.
 */
val DisplayFamily: FontFamily = FontFamily(
    Font(R.font.fraunces_500, FontWeight.Medium),
    Font(R.font.fraunces_600, FontWeight.SemiBold),
)
val UiFamily: FontFamily = FontFamily(
    Font(R.font.figtree_400, FontWeight.Normal),
    Font(R.font.figtree_500, FontWeight.Medium),
    Font(R.font.figtree_700, FontWeight.Bold),
)

/** Design-system §3 type scale mapped onto M3 roles. UI text follows the system font scale (up to 200%, US-021). */
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

/** Tabular figures for the timer and counts (design §3): Figtree's `tnum` is kept in the subset. */
val TimerStyle = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, fontFeatureSettings = "tnum")

/** Game-bar level title (design §3 "game title"). */
val GameTitleStyle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)

/** Larger digits (US-021): board and key digits × 1.2; notes × min(scale, 1.1). Never the system font scale. */
val LocalDigitScale = staticCompositionLocalOf { 1f }

const val LARGE_DIGIT_SCALE = 1.2f

/** The palette for these settings: follow system (or Light/Dark), then the High contrast set of that brightness. */
@Composable
fun paletteFor(theme: ThemeMode, highContrast: Boolean): LanternPalette {
    val dark = when (theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    return Palettes.of(dark, highContrast)
}

/**
 * Provides the palette (Material roles + [LanternColors]), typography and digit scale. Settings changes recompose
 * through here, so a theme switch applies instantly, mid-game included (US-020). No dynamic colour, ever.
 */
@Composable
fun LanternTheme(palette: LanternPalette = Palettes.Light, largeDigits: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLanternColors provides palette.lantern,
        LocalDigitScale provides if (largeDigits) LARGE_DIGIT_SCALE else 1f,
    ) {
        MaterialTheme(colorScheme = palette.colorScheme, typography = LanternTypography, content = content)
    }
}
