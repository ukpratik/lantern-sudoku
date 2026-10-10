// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.model

/** The four difficulty levels, easiest first (PRD US-013). M0 placeholder; techniques per level arrive in M1. */
enum class Level {
    EASY,
    MEDIUM,
    HARD,
    EXPERT,
    ;

    /** The next harder level, or null at [EXPERT]. */
    fun harder(): Level? = entries.getOrNull(ordinal + 1)
}
