// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.model

/** The four difficulty levels, easiest first (PRD US-013). A level's [tier] is the hardest technique tier it allows. */
enum class Level(val code: Char) {
    EASY('E'),
    MEDIUM('M'),
    HARD('H'),
    EXPERT('X'),
    ;

    /** Technique tier 1..4 (LLD §4.2): Easy = singles, Medium = + intersections and naked pair, Hard = + subsets, Expert = + X-Wing. */
    val tier: Int get() = ordinal + 1

    /** The next harder level, or null at [EXPERT]. */
    fun harder(): Level? = entries.getOrNull(ordinal + 1)

    companion object {
        fun ofTier(tier: Int): Level = entries[tier - 1]
        fun ofCode(code: Char): Level = entries.first { it.code == code }
    }
}

/** Which bank a puzzle comes from: New game or Daily (they never share a puzzle, TC-122). */
enum class Pool(val code: Char) {
    NEWGAME('N'),
    DAILY('D'),
    ;

    /** File-name stem used in `bank/v1/` (`newgame-easy.txt`, `daily-expert.txt`). */
    val fileStem: String get() = name.lowercase()

    companion object {
        fun ofCode(code: Char): Pool = entries.first { it.code == code }
    }
}

/** Permanent puzzle ID, `<N|D>-<E|M|H|X>-<index, zero-padded 4>` (ADR-004). */
data class PuzzleId(val pool: Pool, val level: Level, val index: Int) {
    init {
        require(index >= 0) { "index must be >= 0: $index" }
    }

    override fun toString(): String = "${pool.code}-${level.code}-${index.toString().padStart(4, '0')}"

    companion object {
        private val PATTERN = Regex("""([ND])-([EMHX])-(\d{4,})""")

        fun parse(text: String): PuzzleId {
            val m = requireNotNull(PATTERN.matchEntire(text)) { "Not a puzzle ID: '$text'" }
            val (p, l, i) = m.destructured
            return PuzzleId(Pool.ofCode(p[0]), Level.ofCode(l[0]), i.toInt())
        }
    }
}

/** Bank file name for a pool and level, for example `daily-hard.txt`. */
fun bankFileName(pool: Pool, level: Level): String = "${pool.fileStem}-${level.name.lowercase()}.txt"
