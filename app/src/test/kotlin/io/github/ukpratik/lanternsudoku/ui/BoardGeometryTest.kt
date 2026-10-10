// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.ui

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.ui.board.BoardGeometry
import org.junit.Test

/** TC-211: the five worked examples of design-system §4.1 (available height = window minus safeDrawing insets). */
class BoardGeometryTest {
    private fun check(w: Float, availH: Float, board: Float, compact: Boolean, scrolls: Boolean = false) {
        val g = BoardGeometry.compute(w, availH)
        assertThat(g.board).isWithin(0.01f).of(board)
        assertThat(g.compact).isEqualTo(compact)
        assertThat(g.scrolls).isEqualTo(scrolls)
    }

    @Test fun phone390x844_gesture() = check(390f, 844f - 28 - 24, 360f, compact = false)

    @Test fun phone360x740_gesture() = check(360f, 740f - 24 - 24, 344f, compact = true)

    @Test fun phone360x640_threeButton() = check(360f, 640f - 24 - 48, 328f, compact = true)

    @Test fun tablet840x600_landscape_centredColumn() {
        check(840f, 600f - 28 - 24, 308f, compact = true)
        assertThat(BoardGeometry.compute(840f, 548f).column).isEqualTo(480f)
    }

    @Test fun splitScreen400x480_floorAndScroll() {
        check(400f, 480f - 24, 270f, compact = true, scrolls = true)
        assertThat(BoardGeometry.compute(400f, 456f).cell).isWithin(0.01f).of(30f)
    }
}
