// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.game

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.engine.gen.SplitMix64
import org.junit.Test

/**
 * Seeded property tests (LLD §8): 1,000 seeds in CI, 100,000 with `-Plantern.slow=true` (system property
 * `lantern.slow`). No extra library: loops over [SplitMix64].
 */
class ReducerPropertyTest {
    private val seeds = if (System.getProperty("lantern.slow") == "true") 100_000 else 1_000

    /** A random action from everything a player can do on the board (Restart rarely). */
    private fun randomAction(r: SplitMix64): Action = when (r.nextInt(20)) {
        in 0..5 -> Action.TapCell(r.nextInt(81))
        in 6..10 -> Action.TapDigit(1 + r.nextInt(9))
        11 -> Action.LongPressDigit(1 + r.nextInt(9))
        12 -> Action.TapErase
        13 -> Action.ToggleNotes
        14 -> Action.Undo
        15 -> Action.Redo
        16 -> Action.Back
        17 -> listOf(Action.KeepPlaying, Action.KeepLooking, Action.ShowThem)[r.nextInt(3)]
        18 -> if (r.nextInt(50) == 0) Action.Restart else Action.TapCell(r.nextInt(81))
        else -> Action.TapDigit(1 + r.nextInt(9))
    }

    private fun randomRules(r: SplitMix64) =
        Rules(showMistakes = r.nextInt(2) == 0, mistakeLimit = listOf(0, 3, 5)[r.nextInt(3)], autoCleanup = r.nextInt(4) != 0)

    @Test
    fun TC055_selectionAndLockNeverBothSet_andGivensNeverChange() {
        val fresh = Boards.fresh()
        for (seed in 0 until seeds) {
            val r = SplitMix64(seed.toLong())
            val rules = randomRules(r)
            var s = fresh
            repeat(if (seed < 50) 500 else 100) {
                s = s.then(randomAction(r), rules)
                assertThat(s.input.selection != null && s.input.lock != null).isFalse()
                assertThat(s.givens).isEqualTo(fresh.givens)
                for (i in 0 until 81) if (s.isGiven(i)) assertThat(s.code(i)).isEqualTo(0)
                assertThat(s.counters.mistakes).isAtLeast(0)
            }
        }
    }

    @Test
    fun TC064_undoN_thenRedoN_isIdentity() {
        val fresh = Boards.fresh()
        val rules = Rules(showMistakes = true, mistakeLimit = 0)
        for (seed in 0 until seeds) {
            val r = SplitMix64(seed * 7919L + 1)
            var s = fresh
            val moves = if (seed < 20) 500 else 1 + r.nextInt(60)
            var made = 0
            while (made < moves && s.status == Status.PLAYING) {
                // Only board-changing moves, so history length = moves made.
                val before = s.undo.size
                val cell = Boards.empties[r.nextInt(Boards.empties.size)]
                s = s.then(Action.Back, rules).then(Action.Back, rules)
                s = if (r.nextInt(4) == 0) {
                    s.then(Action.TapCell(cell), rules).then(Action.ToggleNotes, rules)
                        .then(Action.TapDigit(1 + r.nextInt(9)), rules).then(Action.ToggleNotes, rules)
                } else {
                    s.then(Action.TapCell(cell), rules).then(Action.TapDigit(1 + r.nextInt(9)), rules)
                }
                if (s.undo.size > before) made++
            }
            if (s.status != Status.PLAYING) continue // solved by chance: nothing to undo through
            val n = s.undo.size
            val after = s.codes()
            var u = s
            repeat(n) { u = u.then(Action.Undo, rules) }
            assertThat(u.codes()).isEqualTo(fresh.codes())
            assertThat(u.undo).isEmpty()
            repeat(n) { u = u.then(Action.Redo, rules) }
            assertThat(u.codes()).isEqualTo(after)
            assertThat(u.redo).isEmpty()
        }
    }

    @Test
    fun autoCleanupUndo_alwaysRestoresNotes() {
        val fresh = Boards.fresh()
        for (seed in 0 until seeds) {
            val r = SplitMix64(seed + 99_000L)
            var s = fresh
            repeat(10) {
                val cell = Boards.empties[r.nextInt(Boards.empties.size)]
                s = s.withNotes(cell, 1 + r.nextInt(9), 1 + r.nextInt(9))
            }
            val target = Boards.empties.first { s.value(it) == 0 }
            val before = s.codes()
            val placed = s.then(Action.TapCell(target)).then(Action.TapDigit(1 + r.nextInt(9)))
            assertThat(placed.then(Action.Undo).codes()).isEqualTo(before)
        }
    }
}
