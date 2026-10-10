// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature

import io.github.ukpratik.lanternsudoku.engine.game.CellCode
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.technique.Technique

/**
 * Real bank positions (bank/v1, part-solved with correct digits by hints) whose next hint starts with each technique of
 * the ladder. Found by replaying hints over the bank; [board] is the givens plus the entries at that moment.
 */
internal data class HintFixture(val technique: Technique, val id: String, val givens: String, val board: String, val note: String = "") {
    fun state(): GameState {
        val g = Grid.parse(givens)
        val b = Grid.parse(board)
        val s = GameState.new(id, g)
        val cells = s.codes()
        for (i in 0 until 81) if (g[i] == 0 && b[i] != 0) cells[i] = CellCode.of(value = b[i], seq = 1)
        return s.copy(cells = cells, seq = 1)
    }
}

internal object HintFixtures {
    val ALL = listOf(
        HintFixture(Technique.FULL_HOUSE, "N-E-0000", ".47...9..91..7.2..6.8..3..4.897....617.624.894....957.7..5..1.2..4.6..97..1...65.",
            ".47...9..91.47.2..6.8..37.4.897..42617.624.894....95717..5..1.2..4.6..97..1...65."),
        HintFixture(Technique.HIDDEN_SINGLE, "N-E-0000", ".47...9..91..7.2..6.8..3..4.897....617.624.894....957.7..5..1.2..4.6..97..1...65.",
            ".47...9..91..7.2..6.8..3..4.897....617.624.894....957.7..5..1.2..4.6..97..1...65."),
        HintFixture(Technique.NAKED_SINGLE, "N-E-0313", "..824.......3.5.8....96.2.4.635.189.7..682..1.214.957.3.2.56....5.8.4.......231..",
            "..8247...2..315.8....9682.4463571892795682..18214395763.2156...15.894.2....7231.."),
        HintFixture(Technique.POINTING, "N-M-0000", ".2438....58.1...2..7...4..64..91....9..548..2....23..47..4...3..4...1.67....6214.",
            "62438...158.17642..7.2.4..64.2917...9..548..2...623..47..4..23.24.831.67...76214.", "then a hidden single"),
        HintFixture(Technique.POINTING, "N-M-0001", ".5.91.....1..73.6.7...2...94.1..8...67.2.5.41...1..8.63...6...5.9.35..7.....89.2.",
            ".5.91.....1..73.6.7...2...94.16.8...6782.5.41...1478.63...6...5.9.35..7.....89.2.", "then a naked single"),
        HintFixture(Technique.BOX_LINE, "N-M-0014", ".46..1.8.87..3.....326..9......4.56.4..7.5..9.95.8......7..389.....9..56.5.8..27.",
            ".46..1.8.87..3.6...326.89..7...4.5684..7.5..9.95.8......7..389.....97.56.5.8..27."),
        HintFixture(Technique.NAKED_PAIR, "N-M-0007", ".95..6.4.1..5.9.363.2.1..5..2.......8.9...6.5.......8..5..8.1.391.7.3..2.3.1..49.",
            "795..6.411845.9.36362.1..5..23....1.8.9..1625..1...38.456982173918743562237165498"),
        HintFixture(Technique.HIDDEN_PAIR, "N-H-0003", "...384.....3..56.2..7.2..9.1..59...3.........2...43..1.8..1.5..3.64..2.....836...",
            "6..384.....39756.2..762139.1..59...3.3.16....2..743..1.8.21.5363.645.2.....836..."),
        HintFixture(Technique.NAKED_TRIPLE, "N-H-0013", "8...5.3....3..2...6.5...4.157...9.....1...7.....4...653.8...2.4...1..5....4.8...3",
            "8...5.3....3..2.5.6.5...4.1576..9.42481.2.739.3.4...653.8...2.4...1..5....428...3"),
        HintFixture(Technique.HIDDEN_TRIPLE, "N-H-0050", "...9...2.....8.6.9....3.7..3.7....485..4.1..349....5.7..3.6....1.8.5.....7...2...",
            "...9...2.....8.6.9....3.7..3.7.9..4858.471..349..2.5.7..3.6....1.8.5.....7...2..."),
        HintFixture(Technique.X_WING, "N-X-0000", "..825.3...2.6...54.........2.....8.9...9.6...6.7.....5.........85...2.7...3.481..",
            "..825.3...2.68..54.........2....5869.8.9.6...6978....5..2...5..85...2.7...35481..", "on rows"),
        HintFixture(Technique.X_WING, "N-X-0001", "8.......5.4...69.1...4.1.3..7..8.1..3.......8..5.4..9..5.2.3...2.97...5.6.......2",
            "8.193...5.438.69.1.9.4.183.97..851..3..1.95.8185.4..9.45.2.3..92.97.8.5.6..594..2", "on columns"),
    )

    /** A chain (D-031): Pointing → Hidden pair → Pointing → Hidden single on N-H-0000. */
    val CHAIN = HintFixture(Technique.POINTING, "N-H-0000", ".84.1....2..5...4..65.9.8....9.....85..1.2..74.....6....6.2.38..5...8..4....4.16.",
        ".84.1....2..58..4..65.9.8..6.9.....85381624974..8.96...46.21385.5.638..48.3.4516.", "chain")
}
