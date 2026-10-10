// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.ui.theme.Palettes
import org.junit.Test

/**
 * LLD §4.11: every M3 role is set (none equals the baseline), and the key WCAG pairs of design §1.3 hold. Light only in
 * M2; M5 extends both checks to the other three palettes (TC-166).
 */
class PaletteTest {
    @Test
    fun noRoleEqualsTheM3Baseline() {
        val baseline = lightColorScheme()
        val ours = Palettes.Light.colorScheme
        // Color is a value class: its getters return the packed Long on the JVM.
        val roles = ColorScheme::class.java.methods.filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == java.lang.Long.TYPE }
        assertThat(roles.size).isAtLeast(36)
        val same = roles.filter { it.invoke(ours) == it.invoke(baseline) }.map { it.name.substringBefore('-') }
        assertThat(same).isEmpty()
    }

    @Test
    fun boardDigitsAndMarksMeetContrast() {
        val k = Palettes.Light.lantern
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
}
