// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.data

import io.github.ukpratik.lanternsudoku.engine.game.GameState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What to write for one game. A newer request for the same game replaces an older pending one (latest wins). */
sealed interface SaveRequest {
    val gameId: Long

    /** The whole saved state (blob + summary) and the play time. */
    data class Full(override val gameId: Long, val state: GameState, val elapsedMs: Long) : SaveRequest

    /** Play time only, about once a second (LLD §4.8). Merged into a pending [Full] if there is one. */
    data class Elapsed(override val gameId: Long, val elapsedMs: Long) : SaveRequest

    /** The completing move: the row becomes COMPLETED and drops its blob (ordered after every earlier save). */
    data class Complete(override val gameId: Long, val state: GameState, val elapsedMs: Long) : SaveRequest
}

/** Performs one write. Returns false when storage refused it (the row is unchanged: each write is one atomic UPDATE). */
fun interface SaveWriter {
    suspend fun write(request: SaveRequest): Boolean
}

/**
 * The autosave pipeline (US-003, ADR-014, LLD §4.8): one consumer in the application scope on the IO dispatcher.
 * - Ordered and never stale: pending requests are kept per game and conflated to the newest, so a write always carries
 *   the latest state offered before it started.
 * - Prompt: a write starts as soon as the consumer is free (typically 5–30 ms), well inside the 500 ms budget.
 * - Survives screen exit: the app scope isn't cancelled when the Game screen goes away.
 * - [flush] awaits every request offered before the call (used on ON_STOP and in tests).
 */
class SaveQueue(scope: CoroutineScope, private val writer: SaveWriter, io: CoroutineDispatcher) {
    private val lock = Any()
    private val pending = LinkedHashMap<Long, SaveRequest>()
    private var offered = 0L
    private val written = MutableStateFlow(0L)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val _lastWriteOk = MutableStateFlow(true)

    /** False after a failed write, true again after the next successful one (drives the save-failed snackbar). */
    val lastWriteOk: StateFlow<Boolean> = _lastWriteOk.asStateFlow()

    init {
        scope.launch(io) {
            for (signal in wake) drain()
        }
    }

    fun offer(request: SaveRequest) {
        synchronized(lock) {
            val old = pending[request.gameId]
            pending[request.gameId] = merge(old, request)
            offered++
        }
        wake.trySend(Unit)
    }

    /** Suspends until everything offered before this call has been written (or has failed). */
    suspend fun flush() {
        val target = synchronized(lock) { offered }
        written.first { it >= target }
    }

    private suspend fun drain() {
        while (true) {
            val (batch, upTo) = synchronized(lock) {
                if (pending.isEmpty()) return
                val b = pending.values.toList()
                pending.clear()
                b to offered
            }
            var ok = true
            for (request in batch) ok = writer.write(request) && ok
            _lastWriteOk.value = ok
            written.value = upTo
        }
    }

    private fun merge(old: SaveRequest?, new: SaveRequest): SaveRequest = when {
        old is SaveRequest.Complete -> old // nothing overrides a completion
        new is SaveRequest.Elapsed && old is SaveRequest.Full -> old.copy(elapsedMs = new.elapsedMs)
        else -> new
    }
}
