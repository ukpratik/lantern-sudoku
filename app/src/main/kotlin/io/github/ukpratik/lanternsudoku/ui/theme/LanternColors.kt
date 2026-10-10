// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Board and pad colours plus the marker strokes that thicken in high contrast (design-system §2). Every field is set
 * by each palette in [Palettes]; nothing falls back to a default.
 */
@Immutable
data class LanternColors(
    val cell: Color,
    val line: Color,
    val boxLine: Color,
    val given: Color,
    val entry: Color,
    val note: Color,
    val selected: Color,
    val selectedRing: Color,
    val glow: Color,
    val peer: Color,
    val sameDigit: Color,
    val noteHighlightBg: Color,
    val noteHighlightFg: Color,
    val conflict: Color,
    val danger: Color,
    val hintHouse: Color,
    val hintTargetRing: Color,
    val hintCauseA: Color,
    val hintCauseB: Color,
    val hintCauseLine: Color,
    val eliminated: Color,
    val keyBg: Color,
    val keyBorder: Color,
    val keyFg: Color,
    val keyCount: Color,
    val lockedKeyBg: Color,
    val lockedKeyFg: Color,
    val lockedKeyRing: Color,
    val lockedKeyCount: Color,
    val doneKeyFg: Color,
    val doneKeyBorder: Color,
    val hintIcon: Color,
    // Ink cards (Continue, Today): design §1.1 inverse-bg / on-inverse(-muted, -accent).
    val inkCard: Color,
    val onInk: Color,
    val onInkMuted: Color,
    val onInkAccent: Color,
    val ringStroke: Dp,
    val boxStroke: Dp,
    val markSize: Dp,
)

val LocalLanternColors = staticCompositionLocalOf<LanternColors> { error("No LanternColors provided") }
