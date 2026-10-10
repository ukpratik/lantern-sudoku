// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.save

import io.github.ukpratik.lanternsudoku.engine.game.Counters
import io.github.ukpratik.lanternsudoku.engine.game.Delta
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.game.InputState
import io.github.ukpratik.lanternsudoku.engine.game.Overrides
import io.github.ukpratik.lanternsudoku.engine.game.Status
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.PuzzleId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Result of decoding a saved blob (LLD §2.3). Errors at the boundary are values, not exceptions (LLD §3.2). */
sealed interface DecodeResult {
    data class Ok(val state: GameState) : DecodeResult
    data class Discarded(val reason: String) : DecodeResult
}

/**
 * The saved game blob, JSON `v = 1` (LLD §2.3). Transient input (selection, lock, notes mode, erase mode) is never saved:
 * a resumed game lands on a neutral board. Play time is not in the blob either; it lives in the `elapsed_ms` column.
 *
 * Versioning: [BlobMigrations] upgrades v → v + 1 on the raw JSON before decoding. Anything that fails, or a version
 * newer than this code knows, is [DecodeResult.Discarded]; the repository then sets the row aside (design
 * `state-save-corrupt`). `ignoreUnknownKeys = false`, so format drift is caught by the fixture tests.
 */
object StateCodec {
    const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        explicitNulls = true
    }

    fun encode(s: GameState): String {
        require(s.status != Status.COMPLETED) { "Completed games drop their blob" }
        return json.encodeToString(StateV1.serializer(), StateV1.of(s))
    }

    fun decode(text: String): DecodeResult = try {
        var obj: JsonObject = json.parseToJsonElement(text).jsonObject
        var v = obj.getValue("v").jsonPrimitive.int
        if (v > VERSION) return DecodeResult.Discarded("blob v$v is newer than v$VERSION")
        if (v < 1) return DecodeResult.Discarded("blob v$v is not a version")
        while (v < VERSION) {
            val step = BlobMigrations.steps[v] ?: return DecodeResult.Discarded("no migration from v$v")
            obj = step(obj)
            v++
        }
        DecodeResult.Ok(json.decodeFromJsonElement(StateV1.serializer(), obj).toState())
    } catch (e: IllegalArgumentException) { // SerializationException, NoSuchElementException-free paths, bad grids
        DecodeResult.Discarded(e.message ?: e.javaClass.simpleName)
    } catch (e: NoSuchElementException) {
        DecodeResult.Discarded(e.message ?: "missing field")
    } catch (e: IndexOutOfBoundsException) {
        DecodeResult.Discarded(e.message ?: "index out of bounds")
    }
}

/**
 * Upgrades from version n to n + 1, keyed by n. Empty while v1 is the only format; every future step gets a test that
 * decodes every frozen `state-v1-*.json` fixture through it (LLD §8).
 */
object BlobMigrations {
    val steps: Map<Int, (JsonObject) -> JsonObject> = emptyMap()
}

@Serializable
internal data class CountersV1(val mistakes: Int, val hints: List<Int>)

@Serializable
internal data class OverridesV1(val showMistakes: Boolean, val limitOff: Boolean)

@Serializable
internal data class StateV1(
    val v: Int,
    val puzzle: String,
    val givens: String,
    val solution: String,
    val cells: List<Int>,
    val undo: List<List<Int>>,
    val redo: List<List<Int>>,
    val seq: Int,
    val counters: CountersV1,
    val overrides: OverridesV1,
    val boxFlags: Int,
    val status: StatusV1,
) {
    fun toState(): GameState {
        require(v == StateCodec.VERSION) { "v$v" }
        PuzzleId.parse(puzzle)
        val g = Grid.parse(givens)
        val sol = Grid.parse(solution)
        require(sol.isFull && sol.isConsistent()) { "solution is not a solved grid" }
        for (i in 0 until Grid.CELLS) require(g[i] == 0 || g[i] == sol[i]) { "givens disagree with the solution at $i" }
        require(cells.size == Grid.CELLS) { "cells: ${cells.size}" }
        require(counters.hints.size == 3) { "hints: ${counters.hints.size}" }
        require(seq >= 0 && counters.mistakes >= 0 && counters.hints.all { it >= 0 }) { "negative counter" }
        val codes = IntArray(Grid.CELLS) { i ->
            val c = cells[i]
            require(c >= 0 && (c and 0xF) <= 9) { "bad cell code $c at $i" }
            require(g[i] == 0 || c == 0) { "a given cell has a code at $i" }
            c
        }
        fun deltas(list: List<List<Int>>) = list.map { t ->
            require(t.size % 3 == 0 && t.isNotEmpty()) { "bad delta size ${t.size}" }
            for (k in t.indices step 3) require(t[k] in 0 until Grid.CELLS && g[t[k]] == 0) { "bad delta cell ${t[k]}" }
            Delta(t.toIntArray())
        }
        return GameState(
            puzzleId = puzzle,
            givens = g,
            solution = sol,
            cells = codes,
            input = InputState(),
            undo = deltas(undo),
            redo = deltas(redo),
            seq = seq,
            counters = Counters(counters.mistakes, counters.hints[0], counters.hints[1], counters.hints[2]),
            overrides = Overrides(overrides.showMistakes, overrides.limitOff),
            boxFlags = boxFlags,
            status = status.status,
        )
    }

    companion object {
        fun of(s: GameState) = StateV1(
            v = StateCodec.VERSION,
            puzzle = s.puzzleId,
            givens = s.givens.toString(),
            solution = s.solution.toString(),
            cells = s.codes().toList(),
            undo = s.undo.map { it.triples.toList() },
            redo = s.redo.map { it.triples.toList() },
            seq = s.seq,
            counters = CountersV1(s.counters.mistakes, listOf(s.counters.hints1, s.counters.hints2, s.counters.hints3)),
            overrides = OverridesV1(s.overrides.showMistakes, s.overrides.limitOff),
            boxFlags = s.boxFlags,
            status = StatusV1.of(s.status),
        )
    }
}

/** Saved statuses: COMPLETED rows drop their blob, so it never appears here. */
@Serializable
internal enum class StatusV1(val status: Status) {
    @SerialName("PLAYING") PLAYING(Status.PLAYING),
    @SerialName("LIMIT") LIMIT(Status.LIMIT),
    @SerialName("ALMOST") ALMOST(Status.ALMOST),
    ;

    companion object {
        fun of(s: Status): StatusV1 = entries.first { it.status == s }
    }
}
