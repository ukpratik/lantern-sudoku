// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.hint

import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.Placement
import io.github.ukpratik.lanternsudoku.engine.solve.CandidateGrid
import io.github.ukpratik.lanternsudoku.engine.technique.StepFinder
import io.github.ukpratik.lanternsudoku.engine.technique.Technique

/**
 * A logical hint: the steps in order (eliminations first, then the single), and the placement they unlock.
 * [searchDepth] is how many simplest-first eliminations the search applied before pruning (0 or 1 for a plain hint).
 */
class LogicalHint(val steps: List<Step>, val placement: Placement, val searchDepth: Int = steps.size - 1) {
    /** True when more than one elimination is shown before the single (LLD §4.4 step 5). */
    val isChain: Boolean get() = steps.size > 2
}

/**
 * The step search behind the hint engine (LLD §4.4 steps 2–5, US-012): on the **true candidates** of a mistake-free
 * board, find the simplest technique plus the placement it unlocks. M3's HintEngine adds error-first, the fallback and
 * the notes overlay around it.
 */
object HintSearch {
    /**
     * Maximum eliminations applied while looking for a single (LLD §4.4 said 6; M1 measured bank states needing up to
     * 12 simplest-first eliminations, so the search goes deeper and then prunes the chain to the steps that matter, D-029).
     */
    const val MAX_CHAIN = 20

    private val ELIMINATORS = Technique.entries.filterNot { it.isSingle }

    /**
     * Searches from the true candidates of [board] (givens plus correct entries). Null means: use the fallback.
     * [maxTier] limits the elimination techniques (4 = the whole ladder); the hint engine first stays within the
     * puzzle's level so a Medium puzzle never names a Hard technique (D-032).
     */
    fun search(board: Grid, maxTier: Int = 4): LogicalHint? = search(CandidateGrid.of(board), maxTier)

    fun search(start: CandidateGrid, maxTier: Int = 4): LogicalHint? {
        val eliminators = ELIMINATORS.filter { it.tier <= maxTier }
        val g = start.copy()
        val chain = ArrayList<Step>()
        for (depth in 0..MAX_CHAIN) {
            // Step 3: a single is available.
            StepFinder.nextSingle(g)?.let { single ->
                return if (chain.size <= 1) LogicalHint(chain + single, single.placement!!) else prune(start, chain, single)
            }
            // Step 4: the simplest elimination instance (technique order, then scan order) that unlocks a single.
            for (t in eliminators) {
                var hit: Pair<Step, Step>? = null
                StepFinder.forEach(t, g) { elim ->
                    val look = g.copy()
                    look.apply(elim)
                    val single = StepFinder.nextSingle(look)
                    if (single != null) hit = elim to single
                    single != null
                }
                hit?.let { (elim, single) ->
                    return if (chain.isEmpty()) LogicalHint(listOf(elim, single), single.placement!!) else prune(start, chain + elim, single)
                }
            }
            // Step 5: nothing unlocks a single directly; apply the simplest elimination and look again.
            if (depth == MAX_CHAIN) break
            val elim = StepFinder.next(g, eliminators.toSet()) ?: break
            g.apply(elim)
            chain += elim
        }
        return null
    }

    /**
     * Drops eliminations the single doesn't need: greedily, earliest first, remove a step if the remaining steps still
     * hold in order (each is still an instance of its technique on the same houses, digits and cause cells) and a single
     * still follows. Deterministic; only runs for chains, which are rare (< 1% of hints on the bank).
     */
    private fun prune(start: CandidateGrid, chain: List<Step>, single: Step): LogicalHint {
        var steps = chain
        var last = single
        var i = 0
        while (i < steps.size && steps.size > 1) {
            val trial = replay(start, steps.filterIndexed { k, _ -> k != i })
            if (trial != null) {
                steps = trial.first
                last = trial.second
            } else {
                i++
            }
        }
        return LogicalHint(steps + last, last.placement!!, chain.size)
    }

    /** Re-applies [steps] from [start], re-finding each one; returns the refreshed steps and the next single, or null. */
    private fun replay(start: CandidateGrid, steps: List<Step>): Pair<List<Step>, Step>? {
        val g = start.copy()
        val out = ArrayList<Step>(steps.size)
        for (s in steps) {
            val again = StepFinder.all(s.technique, g).firstOrNull {
                it.houses.contentEquals(s.houses) && it.digits == s.digits && it.causeCells.contentEquals(s.causeCells)
            } ?: return null
            g.apply(again)
            out += again
        }
        val single = StepFinder.nextSingle(g) ?: return null
        return out to single
    }
}
