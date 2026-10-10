// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.save

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.engine.game.Action
import io.github.ukpratik.lanternsudoku.engine.game.GameReducer
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.game.InputState
import io.github.ukpratik.lanternsudoku.engine.game.Rules
import io.github.ukpratik.lanternsudoku.engine.game.Status
import io.github.ukpratik.lanternsudoku.engine.gen.SplitMix64
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File

/**
 * US-003 / NFR-005: the blob codec round-trips (TC-018), the frozen v1 fixtures decode forever (TC-021), and anything
 * unreadable or from the future is discarded, never crashed on (TC-024).
 *
 * Regenerate the fixtures only on purpose (they are frozen once shipped):
 * `./gradlew :engine:test --tests '*StateCodecTest*' -Plantern.writeFixtures=true`.
 */
class StateCodecTest {
    private val givens = Grid.parse(".2438....58.1...2..7...4..64..91....9..548..2....23..47..4...3..4...1.67....6214.")
    private val fresh = GameState.new("N-M-0000", givens)
    private val empties = (0 until 81).filter { givens[it] == 0 }

    /** A random playable sequence of [moves] board actions (entries, notes, erases, undos, redos). */
    private fun play(seed: Long, moves: Int, rules: Rules = Rules()): GameState {
        val r = SplitMix64(seed)
        var s = fresh
        repeat(moves) {
            if (s.status != Status.PLAYING) return s
            val cell = empties[r.nextInt(empties.size)]
            val action = when (r.nextInt(10)) {
                0 -> Action.Undo
                1 -> Action.Redo
                2 -> Action.TapErase
                3 -> Action.ToggleNotes
                else -> Action.TapDigit(1 + r.nextInt(9))
            }
            // Select the cell (tapping an already selected cell deselects it, so tap again in that case).
            s = GameReducer.reduce(s, Action.TapCell(cell), rules).state
            if (s.input.selection == null) s = GameReducer.reduce(s, Action.TapCell(cell), rules).state
            s = GameReducer.reduce(s, action, rules).state
        }
        return s
    }

    /** Saved states never carry transient input. */
    private fun GameState.neutral() = copy(input = InputState())

    @Test
    fun TC018_roundTripIsLossless_forRandomGames() {
        val seeds = if (System.getProperty("lantern.slow") == "true") 100_000 else 1_000
        for (seed in 0 until seeds) {
            val s = play(seed.toLong(), 1 + (seed % 120), Rules(showMistakes = seed % 2 == 0, mistakeLimit = if (seed % 3 == 0) 3 else 0))
            if (s.status == Status.COMPLETED) continue
            val decoded = StateCodec.decode(StateCodec.encode(s))
            assertThat(decoded).isEqualTo(DecodeResult.Ok(s.neutral()))
        }
    }

    @Test
    fun TC021_frozenV1Fixtures_decode() {
        for (name in listOf("state-v1-empty", "state-v1-500moves", "state-v1-limit", "state-v1-almost")) {
            val text = File(FIXTURES, "$name.json").readText()
            val result = StateCodec.decode(text)
            assertThat(result).isInstanceOf(DecodeResult.Ok::class.java)
            val decoded = (result as DecodeResult.Ok).state
            // Re-encoding gives the same JSON document (format drift fails here, not in a player's save).
            assertThat(Json.parseToJsonElement(StateCodec.encode(decoded))).isEqualTo(Json.parseToJsonElement(text))
        }
        val big = (StateCodec.decode(File(FIXTURES, "state-v1-500moves.json").readText()) as DecodeResult.Ok).state
        assertThat(big.undo.size + big.redo.size).isAtLeast(300)
        assertThat((StateCodec.decode(File(FIXTURES, "state-v1-limit.json").readText()) as DecodeResult.Ok).state.status)
            .isEqualTo(Status.LIMIT)
        assertThat((StateCodec.decode(File(FIXTURES, "state-v1-almost.json").readText()) as DecodeResult.Ok).state.status)
            .isEqualTo(Status.ALMOST)
    }

    @Test
    fun TC024_futureVersion_isDiscarded() {
        val text = StateCodec.encode(fresh).replace("\"v\":1", "\"v\":99")
        val r = StateCodec.decode(text)
        assertThat(r).isInstanceOf(DecodeResult.Discarded::class.java)
        assertThat((r as DecodeResult.Discarded).reason).contains("newer")
    }

    @Test
    fun TC023_corruptBlobs_areDiscarded_neverThrown() {
        val good = StateCodec.encode(fresh)
        val bad = listOf(
            "",
            "{\"v\":1,\"x\":",
            "{}",
            "[]",
            "{\"v\":\"one\"}",
            "{\"v\":0}",
            good.replace("\"seq\":0", "\"seq\":-1"),
            good.replace("\"puzzle\":\"N-M-0000\"", "\"puzzle\":\"nope\""),
            good.replace("\"status\":\"PLAYING\"", "\"status\":\"COMPLETED\""),
            good.replace("\"boxFlags\":0", "\"boxFlags\":0,\"extra\":1"),
            good.replace("\"cells\":[0,", "\"cells\":["),
            good.replace("\"undo\":[]", "\"undo\":[[0,0]]"),
            good.replace("\"undo\":[]", "\"undo\":[[1,0,5]]"), // cell 1 is a given
            good.replace(Regex("\"solution\":\"(\\d)"), "\"solution\":\"."),
        )
        for (text in bad) {
            assertThat(StateCodec.decode(text)).isInstanceOf(DecodeResult.Discarded::class.java)
        }
    }

    @Test
    fun completedGamesHaveNoBlob() {
        val solved = GameState(fresh.puzzleId, givens, fresh.solution, IntArray(81), status = Status.COMPLETED)
        val thrown = runCatching { StateCodec.encode(solved) }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun writeFixtures() {
        if (System.getProperty("lantern.writeFixtures") != "true") return
        FIXTURES.mkdirs()
        for ((name, state) in fixtures()) File(FIXTURES, "$name.json").writeText(StateCodec.encode(state) + "\n")
    }

    /** How the frozen fixtures were made. The committed files are the contract; this only regenerates them on purpose. */
    private fun fixtures(): Map<String, GameState> {
        val limitRules = Rules(showMistakes = true, mistakeLimit = 3)
        var limit = fresh
        for (cell in empties.take(3)) {
            limit = GameReducer.reduce(limit, Action.TapCell(cell), limitRules).state
            limit = GameReducer.reduce(limit, Action.TapDigit((1..9).first { it != fresh.solution[cell] }), limitRules).state
        }
        check(limit.status == Status.LIMIT)

        val hidden = Rules(showMistakes = false)
        val (wrongCell, last) = empties[10] to empties[20]
        val cells = IntArray(81)
        var almost = GameState(fresh.puzzleId, givens, fresh.solution, cells)
        for (i in empties) if (i != wrongCell && i != last) {
            almost = GameReducer.reduce(almost, Action.TapCell(i), hidden).state
            almost = GameReducer.reduce(almost, Action.TapDigit(fresh.solution[i]), hidden).state
        }
        almost = GameReducer.reduce(almost, Action.TapCell(wrongCell), hidden).state
        almost = GameReducer.reduce(almost, Action.TapDigit((1..9).first { it != fresh.solution[wrongCell] }), hidden).state
        almost = GameReducer.reduce(almost, Action.TapCell(last), hidden).state
        almost = GameReducer.reduce(almost, Action.TapDigit(fresh.solution[last]), hidden).state
        check(almost.status == Status.ALMOST)

        return mapOf(
            "state-v1-empty" to fresh,
            "state-v1-500moves" to play(20261010L, 1250),
            "state-v1-limit" to limit,
            "state-v1-almost" to almost,
        ).mapValues { it.value.neutral() }
    }

    private companion object {
        val FIXTURES = File("src/test/resources/fixtures")
    }
}
