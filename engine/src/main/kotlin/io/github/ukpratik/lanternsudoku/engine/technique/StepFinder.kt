// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.technique

import io.github.ukpratik.lanternsudoku.engine.hint.Step
import io.github.ukpratik.lanternsudoku.engine.solve.CandidateGrid

/** Finds technique instances on a [CandidateGrid], always simplest technique first (enum order), then scan order. */
object StepFinder {
    private val FINDERS: Map<Technique, Finder> = java.util.EnumMap<Technique, Finder>(Technique::class.java).apply {
        put(Technique.FULL_HOUSE, FullHouse)
        put(Technique.HIDDEN_SINGLE, HiddenSingle)
        put(Technique.NAKED_SINGLE, NakedSingle)
        put(Technique.POINTING, Pointing)
        put(Technique.BOX_LINE, BoxLine)
        put(Technique.NAKED_PAIR, NakedSubset(2, Technique.NAKED_PAIR))
        put(Technique.HIDDEN_PAIR, HiddenSubset(2, Technique.HIDDEN_PAIR))
        put(Technique.NAKED_TRIPLE, NakedSubset(3, Technique.NAKED_TRIPLE))
        put(Technique.HIDDEN_TRIPLE, HiddenSubset(3, Technique.HIDDEN_TRIPLE))
        put(Technique.X_WING, XWing)
    }

    private val SINGLES = Technique.entries.filter { it.isSingle }

    /** Passes every instance of [technique] to [emit] in scan order until [emit] returns true. Returns true if stopped. */
    fun forEach(technique: Technique, c: CandidateGrid, emit: (Step) -> Boolean): Boolean = FINDERS.getValue(technique).find(c, emit)

    /** Every instance of [technique], in scan order. */
    fun all(technique: Technique, c: CandidateGrid): List<Step> = ArrayList<Step>().also { list -> forEach(technique, c) { list += it; false } }

    /** The first instance of [technique], or null. */
    fun first(technique: Technique, c: CandidateGrid): Step? {
        var found: Step? = null
        forEach(technique, c) { found = it; true }
        return found
    }

    /** The simplest step among [allowed] techniques, or null if none applies. */
    fun next(c: CandidateGrid, allowed: Set<Technique> = Technique.entries.toSet()): Step? {
        for (t in Technique.entries) {
            if (t !in allowed) continue
            first(t, c)?.let { return it }
        }
        return null
    }

    /** The simplest single (full house, hidden single, naked single), or null. */
    fun nextSingle(c: CandidateGrid): Step? {
        for (t in SINGLES) first(t, c)?.let { return it }
        return null
    }
}
