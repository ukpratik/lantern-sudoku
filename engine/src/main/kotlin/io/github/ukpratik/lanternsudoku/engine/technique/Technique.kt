// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.technique

import io.github.ukpratik.lanternsudoku.engine.model.Level

/**
 * The US-013 technique ladder (LLD §4.2). Enum order is the search order: the solver and the hint engine always
 * take the first technique, in this order, that has an instance. [code] is the bank's hardest-technique letter
 * (`a` = FULL_HOUSE … `j` = X_WING, ADR-004) and is permanent.
 */
enum class Technique(val tier: Int, val code: Char, val label: String) {
    FULL_HOUSE(1, 'a', "Full house"),
    HIDDEN_SINGLE(1, 'b', "Hidden single"),
    NAKED_SINGLE(1, 'c', "Naked single"),
    POINTING(2, 'd', "Pointing"),
    BOX_LINE(2, 'e', "Box-line reduction"),
    NAKED_PAIR(2, 'f', "Naked pair"),
    HIDDEN_PAIR(3, 'g', "Hidden pair"),
    NAKED_TRIPLE(3, 'h', "Naked triple"),
    HIDDEN_TRIPLE(3, 'i', "Hidden triple"),
    X_WING(4, 'j', "X-Wing"),
    ;

    /** The level this technique belongs to. */
    val level: Level get() = Level.ofTier(tier)

    /** Singles place a digit; every other technique only eliminates candidates. */
    val isSingle: Boolean get() = tier == 1

    companion object {
        fun ofCode(code: Char): Technique =
            entries.firstOrNull { it.code == code } ?: throw IllegalArgumentException("Unknown technique code '$code'")

        /** Techniques allowed at a level: every technique whose tier is at most the level's tier. */
        fun upTo(level: Level): Set<Technique> = entries.filterTo(java.util.EnumSet.noneOf(Technique::class.java)) { it.tier <= level.tier }
    }
}
