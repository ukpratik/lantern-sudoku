// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.gen

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.engine.bank.BankCodec
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.engine.model.Pool
import io.github.ukpratik.lanternsudoku.engine.solve.BacktrackingSolver
import io.github.ukpratik.lanternsudoku.engine.solve.Grader
import org.junit.Test

class GenTest {
    @Test
    fun splitMix64ReferenceVectors() {
        // Published SplitMix64 output for seed 1234567 (as signed Longs), also checked against an independent Python run.
        val r = SplitMix64(1234567)
        assertThat(List(5) { r.nextLong() }).containsExactly(
            6457827717110365317L, 3203168211198807973L, -8629252141511181193L, 4593380528125082431L, -2037821214251327795L,
        ).inOrder()
        val z = SplitMix64(0)
        assertThat(List(3) { z.nextLong() }).containsExactly(-2152535657050944081L, 7960286522194355700L, 487617019471545679L).inOrder()
    }

    @Test
    fun nextIntReferenceVectors() {
        val r = SplitMix64(42)
        assertThat(List(10) { r.nextInt(10) }).containsExactly(1, 3, 1, 5, 5, 7, 2, 3, 7, 4).inOrder()
    }

    @Test
    fun nextIntStaysInRange() {
        val r = SplitMix64(9)
        repeat(10_000) { assertThat(r.nextInt(7)).isIn(0..6) }
    }

    @Test
    fun generationIsDeterministicPerIndex() {
        for (level in Level.entries) {
            val a = Generator.generate(Pool.DAILY, level, 17)
            val b = Generator.generate(Pool.DAILY, level, 17)
            assertThat(BankCodec.encode(a.record)).isEqualTo(BankCodec.encode(b.record))
        }
    }

    @Test
    fun seedsDifferByPoolLevelAndIndex() {
        val cfg = GenConfig.V1
        val seeds = Pool.entries.flatMap { p -> Level.entries.flatMap { l -> (0 until 50).map { Generator.seed(cfg, p, l, it) } } }
        assertThat(seeds.toSet()).hasSize(seeds.size)
    }

    @Test
    fun generatedRecordsMeetTheirLevelRules() {
        for (level in Level.entries) {
            val r = Generator.generate(Pool.NEWGAME, level, 3).record
            assertThat(r.level).isEqualTo(level)
            assertThat(BacktrackingSolver.countSolutions(r.givens)).isEqualTo(1)
            assertThat(Grader.grade(r.givens).hardest).isEqualTo(r.hardest)
            GenConfig.V1.givensWindow(level)?.let { assertThat(r.givens.filledCount).isIn(it) }
        }
    }

    @Test
    fun filledGridsAreValidAndVary() {
        val rng = SplitMix64(5)
        val a = Generator.fill(rng)
        val b = Generator.fill(rng)
        assertThat(a.isFull && a.isConsistent()).isTrue()
        assertThat(a).isNotEqualTo(b)
    }
}
