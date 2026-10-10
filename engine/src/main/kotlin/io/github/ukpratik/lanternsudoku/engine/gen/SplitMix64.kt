// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.gen

/**
 * SplitMix64 (Steele, Lea and Flood, 2014), specified exactly in LLD §4.6 so every JVM and every Kotlin version gives
 * the same stream (ADR-005). Integer-only; no floating point anywhere.
 */
class SplitMix64(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        return z xor (z ushr 31)
    }

    /** Uniform in `0 until bound`, by rejection sampling on the top 31 bits (no modulo bias). */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive: $bound" }
        val range = 1L shl 31
        val limit = range - range % bound
        while (true) {
            val r = nextLong() ushr 33
            if (r < limit) return (r % bound).toInt()
        }
    }

    /** Fisher–Yates shuffle in place, driven by this stream. */
    fun shuffle(a: IntArray) {
        for (i in a.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val t = a[i]; a[i] = a[j]; a[j] = t
        }
    }

    companion object {
        private const val GOLDEN_GAMMA = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        private const val MIX1 = -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        private const val MIX2 = -0x6b2fb644ecceee15L // 0x94D049BB133111EB
    }
}
