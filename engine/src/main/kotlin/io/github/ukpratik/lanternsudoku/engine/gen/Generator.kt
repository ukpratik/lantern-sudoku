// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.gen

import io.github.ukpratik.lanternsudoku.engine.bank.BankRecord
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.engine.model.Masks
import io.github.ukpratik.lanternsudoku.engine.model.Pool
import io.github.ukpratik.lanternsudoku.engine.solve.BacktrackingSolver
import io.github.ukpratik.lanternsudoku.engine.solve.Grader

/**
 * Frozen generation parameters (ADR-005). Changing any value changes every generated record, so a change means a new
 * [version] and new pools, never a rewrite of shipped ones.
 */
data class GenConfig(
    val version: Int = 1,
    val bankSeed: Long = 0x4C414E5445524E31L, // "LANTERN1"
    val easyGivens: IntRange = 36..38,
    val mediumGivens: IntRange = 32..34,
) {
    /** Non-minimal givens window for Easy and Medium (D-014); null means dig to minimal (Hard, Expert). */
    fun givensWindow(level: Level): IntRange? = when (level) {
        Level.EASY -> easyGivens
        Level.MEDIUM -> mediumGivens
        Level.HARD, Level.EXPERT -> null
    }

    companion object {
        val V1 = GenConfig()
    }
}

/** A generated record plus how many solution grids were tried (for the yield report). */
class GenResult(val record: BankRecord, val attempts: Int)

/**
 * Deterministic puzzle generator (LLD §4.6). One record depends only on (config, pool, level, index): its own
 * SplitMix64 stream, no shared state, no clocks, no hash-ordered collections, no floating point. Callers may therefore
 * generate indices in parallel and assemble them in index order.
 */
object Generator {
    /** The per-record seed: `mix(BANK_SEED, pool, level, index)`. */
    fun seed(cfg: GenConfig, pool: Pool, level: Level, index: Int): Long {
        val key = cfg.bankSeed xor (pool.ordinal.toLong() shl 56) xor (level.ordinal.toLong() shl 48) xor index.toLong()
        return SplitMix64(key).nextLong()
    }

    fun generate(pool: Pool, level: Level, index: Int, cfg: GenConfig = GenConfig.V1): GenResult {
        val rng = SplitMix64(seed(cfg, pool, level, index))
        var attempts = 0
        while (true) {
            attempts++
            val solution = fill(rng)
            val record = dig(solution, level, cfg.givensWindow(level), rng)
            if (record != null) return GenResult(record, attempts)
        }
    }

    /** Step 1: a random complete grid (backtracking with an RNG-shuffled digit order at every node). */
    internal fun fill(rng: SplitMix64): Grid =
        checkNotNull(BacktrackingSolver.fill(Grid.EMPTY) { mask -> Masks.digits(mask).also(rng::shuffle) })

    /**
     * Steps 2–3: remove 180°-symmetric cell pairs in an RNG-shuffled order while the solution stays unique.
     * Easy/Medium stop inside their givens window (at an RNG-chosen target, then keep trying lower while still in the
     * window); Hard/Expert dig to minimal. Returns the record if its grade is exactly [level], else null.
     */
    internal fun dig(solution: Grid, level: Level, window: IntRange?, rng: SplitMix64): BankRecord? {
        val order = IntArray(41) { it } // cell p pairs with 80 - p; 40 is the centre
        rng.shuffle(order)
        val target = window?.let { it.first + rng.nextInt(it.last - it.first + 1) }
        val cells = solution.toIntArray()
        var givens = 81
        for (p in order) {
            val q = 80 - p
            val removed = if (p == q) 1 else 2
            if (window != null && givens - removed < window.first) continue
            val vp = cells[p]
            val vq = cells[q]
            cells[p] = 0
            cells[q] = 0
            if (BacktrackingSolver.countSolutions(Grid(cells), 2) != 1) {
                cells[p] = vp
                cells[q] = vq
                continue
            }
            givens -= removed
            if (target != null && givens <= target) {
                val g = Grader.grade(Grid(cells))
                if (g.level == level) return BankRecord(Grid(cells), g.hardest!!)
                if (g.level == null || g.level > level) return null // digging further only gets harder
            }
        }
        if (window != null) return null
        val g = Grader.grade(Grid(cells))
        return if (g.level == level) BankRecord(Grid(cells), g.hardest!!) else null
    }
}
