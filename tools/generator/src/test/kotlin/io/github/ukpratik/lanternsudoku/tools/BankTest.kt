// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.tools

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.engine.bank.BankCodec
import io.github.ukpratik.lanternsudoku.engine.gen.Generator
import io.github.ukpratik.lanternsudoku.engine.hint.HintSearch
import io.github.ukpratik.lanternsudoku.engine.solve.BacktrackingSolver
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Tests over the committed bank (`-Dlantern.bank`, set by Gradle). */
class BankTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val bank = File(System.getProperty("lantern.bank") ?: "../../bank/v1")

    private fun copyBank(): File = tmp.newFolder("v1").also { dst -> bank.listFiles()!!.forEach { it.copyTo(File(dst, it.name)) } }

    @Test
    fun firstRecordsRegenerateByteIdentically() {
        // A cheap slice of TC-119 (the full regeneration takes about a minute on 14 cores).
        for (bf in BankFile.ALL) {
            val bytes = bf.bytes(bank)
            for (i in 0 until 2) {
                val again = BankCodec.encode(Generator.generate(bf.pool, bf.level, i).record)
                assertThat(again).isEqualTo(bytes.copyOfRange(i * 84, i * 84 + 84))
            }
        }
    }

    @Test
    fun hintStepSearchIsFastOnBankPuzzles() {
        // NFR-002 / TC-124: ≤ 200 ms on the reference device; the JVM gate is < 50 ms worst case per search. Every 20th
        // record of every file, whole solves (one search per state), after a warm-up. Also TC-112's invariants.
        val warm = BankCodec.decode(BankFile.ALL[3].bytes(bank), 0).givens
        repeat(20) { HintSearch.search(warm) }
        val total = ReplayStats()
        for (bf in BankFile.ALL) {
            val records = BankCodec.decodeAll(bf.bytes(bank))
            for (i in records.indices step 20) {
                val g = records[i].givens
                total.add(HintReplay.replay(g, BacktrackingSolver.solve(g)!!))
            }
        }
        println("hint search: ${total.hints} searches, mean ${"%.1f".format(total.meanMicros)} µs, max ${"%.2f".format(total.maxMillis)} ms")
        assertThat(total.fallbacks).isEqualTo(0)
        assertThat(total.wrongPlacements).isEqualTo(0)
        assertThat(total.badEliminations).isEqualTo(0)
        assertThat(total.maxMillis).isLessThan(50.0)
        assertThat(total.meanMicros).isLessThan(2_000.0)
    }

    @Test
    fun committedBankValidates() {
        val lines = ArrayList<String>()
        assertThat(ValidateCmd.validate(bank, 4, lines::add)).isEmpty()
    }

    @Test
    fun editingAShippedRecordFailsTheFrozenPrefix() {
        // TC-118: change one character of newgame-hard record 5 (keeping the record itself valid-looking).
        val dir = copyBank()
        val f = File(dir, "newgame-hard.txt")
        val bytes = f.readBytes()
        val other = BankCodec.encode(BankCodec.decode(bytes, 6 * 84))
        other.copyInto(bytes, 5 * 84) // record 5 := record 6 (also a duplicate)
        f.writeBytes(bytes)
        val errors = ValidateCmd.validate(dir, 4) {}
        assertThat(errors.any { it.contains("newgame-hard.txt: frozen prefix 0.1.0") && it.contains("SHA-256 mismatch") }).isTrue()
        assertThat(errors.any { it.contains("N-H-0006 duplicates N-H-0005") }).isTrue()
    }

    @Test
    fun duplicatesAcrossPoolsAreCaught() {
        // TC-122: append a New-game record to the daily file (also fails the manifest count).
        val dir = copyBank()
        val record = BankFile.ALL[0].bytes(dir).copyOf(84)
        File(dir, "daily-easy.txt").appendBytes(record)
        val errors = ValidateCmd.validate(dir, 4) {}
        assertThat(errors.any { it.contains("D-E-1040 duplicates N-E-0000") }).isTrue()
        assertThat(errors.any { it.contains("manifest count") }).isTrue()
    }

    @Test
    fun wrongGradeAndBrokenRecordsAreCaught() {
        val dir = copyBank()
        val f = File(dir, "newgame-expert.txt")
        val bytes = f.readBytes()
        bytes[82] = 'g'.code.toByte() // record 0 claims Hidden pair: wrong level for the file
        bytes[84 + 82] = 'z'.code.toByte() // record 1: unknown code
        f.writeBytes(bytes)
        val errors = ValidateCmd.validate(dir, 4) {}
        assertThat(errors.any { it.startsWith("N-X-0000: code 'g'") }).isTrue()
        assertThat(errors.any { it.startsWith("N-X-0001: Unknown technique code") }).isTrue()
    }

    @Test
    fun recordRulesCatchNonUniqueAndOutOfWindowPuzzles() {
        val easy = BankFile.ALL[0]
        val rec = BankCodec.decode(easy.bytes(bank), 0)
        // Easy is non-minimal, so drop three givens: below the 36–38 window (or no longer unique).
        var g = rec.givens
        repeat(3) { g = g.with((0 until 81).first { g[it] != 0 }, 0) }
        assertThat(ValidateCmd.checkRecord(easy, 0, BankCodec.encode(rec.copy(givens = g)))).isNotNull()
        val hard = BankFile.ALL[2]
        val hrec = BankCodec.decode(hard.bytes(bank), 0)
        val hg = (0 until 81).first { hrec.givens[it] != 0 }
        assertThat(ValidateCmd.checkRecord(hard, 0, BankCodec.encode(hrec.copy(givens = hrec.givens.with(hg, 0)))))
            .contains("solutions")
    }
}
