// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.data

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.engine.game.Action
import io.github.ukpratik.lanternsudoku.engine.game.GameReducer
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.game.Rules
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Collections

/** US-003 / ADR-014: ordered, conflated, never-stale autosave; flush; failure reporting. */
@OptIn(ExperimentalCoroutinesApi::class)
class SaveQueueTest {
    private val givens = Grid.parse(".2438....58.1...2..7...4..64..91....9..548..2....23..47..4...3..4...1.67....6214.")
    private val empties = (0 until 81).filter { givens[it] == 0 }

    /** States after 0..n moves (each move places a digit in the next empty cell). */
    private fun states(n: Int): List<GameState> {
        var s = GameState.new("N-M-0000", givens)
        val out = mutableListOf(s)
        repeat(n) { k ->
            s = GameReducer.reduce(s, Action.TapCell(empties[k]), Rules()).state
            s = GameReducer.reduce(s, Action.TapDigit(s.solution[empties[k]]), Rules()).state
            out += s
        }
        return out
    }

    @Test
    fun writesAreOrdered_conflated_andTheLastWriteIsTheLatestState() = runTest {
        val written = Collections.synchronizedList(mutableListOf<Int>())
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val writer = SaveWriter { r ->
            if (!started.isCompleted) { // hold the first write so later offers pile up
                started.complete(Unit)
                gate.await()
            }
            written += (r as SaveRequest.Full).state.undo.size
            true
        }
        val queue = SaveQueue(backgroundScope, writer, StandardTestDispatcher(testScheduler))
        val all = states(40)
        queue.offer(SaveRequest.Full(1, all[1], 0))
        started.await()
        for (k in 2..40) queue.offer(SaveRequest.Full(1, all[k], k * 100L))
        gate.complete(Unit)
        queue.flush()
        // Ordered and never stale: strictly increasing, and the final write is the newest state.
        assertThat(written).isInOrder()
        assertThat(written.last()).isEqualTo(40)
        // Conflated: the 39 offers made while a write was in flight became a single write.
        assertThat(written).containsExactly(1, 40).inOrder()
    }

    @Test
    fun elapsedMergesIntoAPendingFull_andCompleteIsNeverOverridden() = runTest {
        val seen = Collections.synchronizedList(mutableListOf<SaveRequest>())
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val queue = SaveQueue(backgroundScope, { r ->
            if (!started.isCompleted) {
                started.complete(Unit)
                gate.await()
            }
            seen += r
            true
        }, StandardTestDispatcher(testScheduler))
        val s = states(2)
        queue.offer(SaveRequest.Elapsed(9, 1)) // in flight
        started.await()
        queue.offer(SaveRequest.Full(1, s[1], 1_000))
        queue.offer(SaveRequest.Elapsed(1, 2_000))
        queue.offer(SaveRequest.Complete(2, s[2], 3_000))
        queue.offer(SaveRequest.Full(2, s[1], 4_000))
        gate.complete(Unit)
        queue.flush()
        assertThat(seen.drop(1)).containsExactly(SaveRequest.Full(1, s[1], 2_000), SaveRequest.Complete(2, s[2], 3_000))
    }

    @Test
    fun aFailedWriteIsReported_andTheNextSuccessClearsIt() = runTest {
        var fail = true
        val queue = SaveQueue(backgroundScope, { !fail }, StandardTestDispatcher(testScheduler))
        val s = states(1)
        queue.offer(SaveRequest.Full(1, s[0], 0))
        queue.flush()
        assertThat(queue.lastWriteOk.value).isFalse()
        fail = false
        queue.offer(SaveRequest.Full(1, s[1], 0))
        queue.flush()
        assertThat(queue.lastWriteOk.value).isTrue()
    }

    @Test
    fun flushWithNothingPending_returnsImmediately() = runTest {
        val queue = SaveQueue(backgroundScope, { true }, StandardTestDispatcher(testScheduler))
        val done = CompletableDeferred<Unit>()
        launch { queue.flush(); done.complete(Unit) }
        advanceUntilIdle()
        assertThat(done.isCompleted).isTrue()
    }
}
