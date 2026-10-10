// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.ukpratik.lanternsudoku.ui.theme.Palettes
import org.junit.Test

/**
 * LLD §4.11, US-020, TC-166: in all four palettes every M3 role is set (none equals the baseline scheme of the same
 * brightness), and the key WCAG pairs of design §1.3 hold.
 */
class PaletteTest {
    // Color is a value class: its getters return the packed Long on the JVM.
    private val roles = ColorScheme::class.java.methods
        .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == java.lang.Long.TYPE }
        .associateBy { it.name.removePrefix("get").substringBefore('-').replaceFirstChar(Char::lowercase) }

    @Test
    fun everyRoleIsTheDesignToken() {
        assertThat(roles.size).isAtLeast(36)
        for ((p, theme) in Palettes.all.zip(listOf("light", "dark", "hc-light", "hc-dark"))) {
            val tokens = Tokens.of(theme)
            for ((role, getter) in roles) {
                val token = ROLE_TOKENS[role] ?: error("role $role has no design token (design-system §2)")
                val expected = if (token.isEmpty()) Color.Transparent else tokens.getValue(token)
                assertWithMessage("$theme $role = --$token").that(Color((getter.invoke(p.colorScheme) as Long).toULong())).isEqualTo(expected)
            }
        }
    }

    @Test
    fun noRoleEqualsTheM3Baseline() {
        for ((p, theme) in Palettes.all.zip(listOf("light", "dark", "hc-light", "hc-dark"))) {
            val baseline = if (p.dark) darkColorScheme() else lightColorScheme()
            val tokens = Tokens.of(theme)
            val same = roles.filter { (_, g) -> g.invoke(p.colorScheme) == g.invoke(baseline) }.keys
            // A match is only allowed where the design token itself is that value (HC light's pure white on-colours,
            // the black scrim): an omitted role, which falls back to baseline purple, always fails.
            val deliberate = same.filter { role -> ROLE_TOKENS[role]?.takeIf { it.isNotEmpty() }?.let { tokens[it] } == Color((roles.getValue(role).invoke(baseline) as Long).toULong()) }
            assertWithMessage(theme).that(same - deliberate.toSet()).isEmpty()
        }
    }

    @Test
    fun paletteSelection() {
        assertThat(Palettes.of(dark = false, highContrast = false)).isSameInstanceAs(Palettes.Light)
        assertThat(Palettes.of(dark = true, highContrast = false)).isSameInstanceAs(Palettes.Dark)
        assertThat(Palettes.of(dark = false, highContrast = true)).isSameInstanceAs(Palettes.HcLight)
        assertThat(Palettes.of(dark = true, highContrast = true)).isSameInstanceAs(Palettes.HcDark)
        assertThat(Palettes.all.map { it.colorScheme.background }.toSet()).hasSize(4)
        // HC thickens the markers (tokens.css --stroke-ring / --mark-size).
        assertThat(Palettes.HcLight.lantern.ringStroke).isGreaterThan(Palettes.Light.lantern.ringStroke)
        assertThat(Palettes.HcDark.lantern.markSize).isGreaterThan(Palettes.Dark.lantern.markSize)
    }

    @Test
    fun boardDigitsAndMarksMeetContrast() {
        for (p in Palettes.all) checkContrast(p.lantern)
    }

    private fun checkContrast(k: io.github.ukpratik.lanternsudoku.ui.theme.LanternColors) {
        val fills = listOf(k.cell, k.peer, k.sameDigit, k.selected, k.conflict)
        for (bg in fills) {
            assertThat(contrast(k.given, bg)).isAtLeast(4.5)
            assertThat(contrast(k.entry, bg)).isAtLeast(4.5)
            assertThat(contrast(k.note, bg)).isAtLeast(4.5)
            assertThat(contrast(k.danger, bg)).isAtLeast(4.5)
        }
        assertThat(contrast(k.noteHighlightFg, k.noteHighlightBg)).isAtLeast(4.5)
        assertThat(contrast(k.boxLine, k.cell)).isAtLeast(3.0)
        assertThat(contrast(k.line, k.cell)).isAtLeast(3.0)
        assertThat(contrast(k.selectedRing, k.selected)).isAtLeast(3.0)
        assertThat(contrast(k.danger, k.conflict)).isAtLeast(3.0)
        assertThat(contrast(k.keyFg, k.keyBg)).isAtLeast(4.5)
        assertThat(contrast(k.keyCount, k.keyBg)).isAtLeast(4.5)
        assertThat(contrast(k.lockedKeyFg, k.lockedKeyBg)).isAtLeast(4.5)
        assertThat(contrast(k.onInk, k.inkCard)).isAtLeast(4.5)
        assertThat(contrast(k.onInkMuted, k.inkCard)).isAtLeast(4.5)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb).toDouble() / minOf(la, lb)
    }

    /** design-system §2: M3 role → tokens.css custom property ("" = Transparent). */
    private val ROLE_TOKENS = mapOf(
        "primary" to "accent-strong", "onPrimary" to "bg", "primaryContainer" to "accent", "onPrimaryContainer" to "on-accent",
        "inversePrimary" to "on-inverse-accent", "secondary" to "text", "onSecondary" to "bg", "secondaryContainer" to "accent-soft",
        "onSecondaryContainer" to "on-accent-soft", "tertiary" to "entry", "onTertiary" to "bg", "tertiaryContainer" to "same",
        "onTertiaryContainer" to "text", "background" to "bg", "onBackground" to "text", "surface" to "bg", "onSurface" to "text",
        "surfaceVariant" to "surface", "onSurfaceVariant" to "text-muted", "surfaceTint" to "", "inverseSurface" to "inverse-bg",
        "inverseOnSurface" to "on-inverse", "error" to "danger-text", "onError" to "bg", "errorContainer" to "conflict-bg",
        "onErrorContainer" to "on-error-container", "outline" to "outline-strong", "outlineVariant" to "outline", "scrim" to "scrim-base",
        "surfaceBright" to "surface-bright", "surfaceContainer" to "surface-container", "surfaceContainerHigh" to "surface-container-high",
        "surfaceContainerHighest" to "surface-container-highest", "surfaceContainerLow" to "surface-container-low",
        "surfaceContainerLowest" to "surface-container-lowest", "surfaceDim" to "surface-dim",
        "primaryFixed" to "accent-soft", "primaryFixedDim" to "accent", "onPrimaryFixed" to "on-accent-soft", "onPrimaryFixedVariant" to "accent-text",
        "secondaryFixed" to "accent-soft", "secondaryFixedDim" to "accent", "onSecondaryFixed" to "on-accent-soft", "onSecondaryFixedVariant" to "accent-text",
        "tertiaryFixed" to "same", "tertiaryFixedDim" to "same", "onTertiaryFixed" to "text", "onTertiaryFixedVariant" to "text",
    )

    /** Reads the hex colours of one theme block straight from the design's tokens.css (the source of truth). */
    private object Tokens {
        private val css = java.io.File("../product/04-design/final/assets/tokens.css").readText()

        fun of(theme: String): Map<String, Color> {
            val selector = if (theme == "light") ":root, [data-theme=\"light\"] {" else "[data-theme=\"$theme\"] {"
            val start = css.indexOf(selector)
            check(start >= 0) { "no $theme block in tokens.css" }
            val block = css.substring(start, css.indexOf("\n}", start))
            return Regex("""--([\w-]+):\s*#([0-9A-Fa-f]{6});""").findAll(block)
                .associate { it.groupValues[1] to Color(0xFF000000 or it.groupValues[2].toLong(16)) }
        }
    }
}
