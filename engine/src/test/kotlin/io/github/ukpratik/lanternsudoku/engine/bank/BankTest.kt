// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.bank

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.engine.model.Pool
import io.github.ukpratik.lanternsudoku.engine.model.PuzzleId
import io.github.ukpratik.lanternsudoku.engine.technique.Technique
import org.junit.Assert.assertThrows
import org.junit.Test

class BankTest {
    private val record = BankRecord(Grid.parse("..825.3...2.6...54.........2.....8.9...9.6...6.7.....5.........85...2.7...3.481.."), Technique.X_WING)

    @Test
    fun codecRoundTripsAndIsExactly84Bytes() {
        val bytes = BankCodec.encode(record)
        assertThat(bytes).hasLength(84)
        assertThat(String(bytes, Charsets.US_ASCII)).isEqualTo("..825.3...2.6...54.........2.....8.9...9.6...6.7.....5.........85...2.7...3.481.. j\n")
        assertThat(BankCodec.decode(bytes)).isEqualTo(record)
        for (t in Technique.entries) {
            val r = record.copy(hardest = t)
            assertThat(BankCodec.decode(BankCodec.encode(r))).isEqualTo(r)
        }
    }

    @Test
    fun recordIIsAtByte84TimesI() {
        val other = record.copy(hardest = Technique.HIDDEN_PAIR)
        val file = BankCodec.encode(record) + BankCodec.encode(other)
        assertThat(BankCodec.decode(file, 84)).isEqualTo(other)
        assertThat(BankCodec.decodeAll(file)).containsExactly(record, other).inOrder()
        assertThat(BankCodec.count(168)).isEqualTo(2)
    }

    @Test
    fun malformedRecordsAreRejected() {
        val bytes = BankCodec.encode(record)
        assertThrows(BankFormatException::class.java) { BankCodec.decode(bytes.copyOf().also { it[0] = 'x'.code.toByte() }) }
        assertThrows(BankFormatException::class.java) { BankCodec.decode(bytes.copyOf().also { it[82] = 'z'.code.toByte() }) }
        assertThrows(BankFormatException::class.java) { BankCodec.decode(bytes.copyOf().also { it[83] = ' '.code.toByte() }) }
        assertThrows(BankFormatException::class.java) { BankCodec.decode(bytes, 1) }
        assertThrows(BankFormatException::class.java) { BankCodec.count(85) }
    }

    @Test
    fun puzzleIdFormatAndParse() {
        val id = PuzzleId(Pool.NEWGAME, Level.HARD, 123)
        assertThat(id.toString()).isEqualTo("N-H-0123")
        assertThat(PuzzleId.parse("D-X-0519")).isEqualTo(PuzzleId(Pool.DAILY, Level.EXPERT, 519))
        assertThat(PuzzleId.parse(id.toString())).isEqualTo(id)
        assertThrows(IllegalArgumentException::class.java) { PuzzleId.parse("N-Q-0001") }
    }

    @Test
    fun manifestRoundTripsThroughJson() {
        val m = Manifest(
            1, 1,
            listOf(
                ManifestFile("newgame-easy.txt", Pool.NEWGAME, Level.EASY, 3, listOf(FrozenPrefix("0.1.0", 2, "ab"), FrozenPrefix("1.0.0", 3, "cd"))),
                ManifestFile("daily-expert.txt", Pool.DAILY, Level.EXPERT, 0, emptyList()),
            ),
        )
        assertThat(Manifest.parse(m.toJson())).isEqualTo(m)
    }

    @Test
    fun manifestParserIsStrict() {
        assertThrows(IllegalArgumentException::class.java) { Manifest.parse("""{"format": 1, "format": 2}""") }
        assertThrows(IllegalArgumentException::class.java) { Manifest.parse("""{"format": 1} x""") }
    }

    @Test
    fun sha256OfAPrefix() {
        assertThat(Manifest.sha256("abc".toByteArray())).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        assertThat(Manifest.sha256("abcdef".toByteArray(), 3)).isEqualTo(Manifest.sha256("abc".toByteArray()))
    }
}
