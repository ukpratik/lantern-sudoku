// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LevelTest {
    @Test
    fun levelsAreOrderedEasiestFirst() {
        assertThat(Level.entries).containsExactly(Level.EASY, Level.MEDIUM, Level.HARD, Level.EXPERT).inOrder()
    }

    @Test
    fun harderStepsUpAndStopsAtExpert() {
        assertThat(Level.EASY.harder()).isEqualTo(Level.MEDIUM)
        assertThat(Level.HARD.harder()).isEqualTo(Level.EXPERT)
        assertThat(Level.EXPERT.harder()).isNull()
    }
}
