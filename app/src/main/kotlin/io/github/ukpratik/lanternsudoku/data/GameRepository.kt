// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.data

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import io.github.ukpratik.lanternsudoku.data.db.ContinueRow
import io.github.ukpratik.lanternsudoku.data.db.GameDao
import io.github.ukpratik.lanternsudoku.data.db.GameEntity
import io.github.ukpratik.lanternsudoku.data.db.LanternDatabase
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.save.DecodeResult
import io.github.ukpratik.lanternsudoku.engine.save.StateCodec
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.engine.model.Pool
import io.github.ukpratik.lanternsudoku.engine.model.PuzzleId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Home's view of a saved game (US-001): level, time, progress and the mini-board summary. */
data class ContinueSummary(val gameId: Long, val level: Level, val daily: Boolean, val elapsedMs: Long, val filled: Int, val gridSummary: String)

sealed interface LoadResult {
    data class Ok(val state: GameState, val elapsedMs: Long, val level: Level) : LoadResult

    /** The save couldn't be read or migrated; the row is now DISCARDED (design `state-save-corrupt`). */
    data class Discarded(val reason: String) : LoadResult

    /** No such in-progress game (already completed or abandoned elsewhere). */
    data object Missing : LoadResult
}

/** The completion facts the summary shows (US-018 details arrive in M4). */
data class CompletionSummary(val level: Level, val elapsedMs: Long, val mistakes: Int)

/**
 * Games: start, load, save, complete, abandon (LLD §3.2). Invariants enforced here:
 * at most one in-progress New game per level; the blob is non-null only while in progress.
 */
class GameRepository(
    private val db: LanternDatabase,
    private val source: PuzzleSource,
    private val io: CoroutineDispatcher,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : SaveWriter {
    private val dao: GameDao = db.gameDao()
    private val _discardNotice = MutableStateFlow(false)

    /** True after a save was set aside because it couldn't be read; Home shows the one-line notice until dismissed. */
    val discardNotice: StateFlow<Boolean> = _discardNotice.asStateFlow()

    fun dismissDiscardNotice() {
        _discardNotice.value = false
    }

    fun continueCard(): Flow<ContinueSummary?> = dao.continueCard().map { it?.toSummary() }

    fun inProgressLevels(): Flow<Set<Level>> = dao.inProgressLevels().map { list -> list.mapTo(HashSet()) { Level.entries[it] } }

    /** True until the first game row exists (Home's first-run layout, design `play-tab-first-run`). */
    fun isFirstRun(): Flow<Boolean> = dao.gameCount().map { it == 0 }

    suspend fun inProgress(level: Level): ContinueSummary? = withContext(io) { dao.inProgress(level.ordinal)?.toSummary() }

    /**
     * Starts a New game at [level] (US-002). If one is already in progress it is returned unchanged, unless
     * [abandonExisting], in which case it is marked ABANDONED first ("Start new", PRD §5) in the same transaction.
     */
    suspend fun startNew(level: Level, abandonExisting: Boolean = false): Long = withContext(io) {
        val id = PuzzleId(Pool.NEWGAME, level, pickIndex(level))
        startWith(id, abandonExisting)
    }

    /** Starts a specific puzzle (debug hook `dbg:load`, and tests). Same invariants as [startNew]. */
    suspend fun startWith(id: PuzzleId, abandonExisting: Boolean = true): Long = withContext(io) {
        require(id.pool == Pool.NEWGAME) { "Dailies are opened by date (M4)" }
        val record = source.read(id)
        val state = GameState.new(id.toString(), record.givens)
        val now = clock.millis()
        db.withTransaction {
            val existing = dao.inProgress(id.level.ordinal)
            if (existing != null) {
                if (!abandonExisting) return@withTransaction existing.id
                dao.abandon(existing.id, now)
            }
            _discardNotice.value = false // starting any game dismisses the notice (design `state-save-corrupt`)
            dao.insert(
                GameEntity(
                    puzzleId = id.toString(),
                    kind = GameEntity.KIND_NEW,
                    level = id.level.ordinal,
                    status = GameEntity.IN_PROGRESS,
                    startedAt = now,
                    startedEpochDay = today(),
                    updatedAt = now,
                    filledCount = state.filledCount,
                    gridSummary = GameState.gridSummary(state),
                    stateVersion = StateCodec.VERSION,
                    stateBlob = StateCodec.encode(state),
                ),
            )
        }
    }

    suspend fun abandon(gameId: Long) {
        withContext(io) { dao.abandon(gameId, clock.millis()) }
    }

    suspend fun load(gameId: Long): LoadResult = withContext(io) {
        val row = dao.get(gameId) ?: return@withContext LoadResult.Missing
        if (row.status != GameEntity.IN_PROGRESS) return@withContext LoadResult.Missing
        val blob = row.stateBlob ?: return@withContext discard(gameId, "no blob")
        when (val r = StateCodec.decode(blob)) {
            is DecodeResult.Ok -> LoadResult.Ok(r.state, row.elapsedMs, Level.entries[row.level])
            is DecodeResult.Discarded -> discard(gameId, r.reason)
        }
    }

    /**
     * Cold-start check (design `state-save-corrupt`): decodes every in-progress blob and sets aside the unreadable
     * ones, so Home never offers a Continue card that can't open. Returns how many were discarded.
     */
    suspend fun verifyInProgress(): Int = withContext(io) {
        var discarded = 0
        for (row in dao.inProgressBlobs()) {
            val ok = row.stateBlob?.let { StateCodec.decode(it) } is DecodeResult.Ok
            if (!ok) {
                discard(row.id, "unreadable on start")
                discarded++
            }
        }
        discarded
    }

    private suspend fun discard(gameId: Long, reason: String): LoadResult.Discarded {
        dao.discard(gameId, clock.millis())
        _discardNotice.value = true
        return LoadResult.Discarded(reason)
    }

    /** Called on the IO dispatcher by [SaveQueue]. Storage failures roll back (one UPDATE) and report false. */
    override suspend fun write(request: SaveRequest): Boolean = try {
        when (request) {
            is SaveRequest.Full -> {
                val s = request.state
                dao.saveState(
                    id = request.gameId,
                    blob = StateCodec.encode(s),
                    version = StateCodec.VERSION,
                    elapsedMs = request.elapsedMs,
                    mistakes = s.counters.mistakes,
                    hints1 = s.counters.hints1,
                    hints2 = s.counters.hints2,
                    hints3 = s.counters.hints3,
                    filledCount = s.filledCount,
                    gridSummary = GameState.gridSummary(s),
                    boxFlags = s.boxFlags,
                    now = clock.millis(),
                )
            }
            is SaveRequest.Elapsed -> dao.saveElapsed(request.gameId, request.elapsedMs)
            is SaveRequest.Complete -> {
                val s = request.state
                dao.complete(
                    id = request.gameId,
                    now = clock.millis(),
                    day = today(),
                    elapsedMs = request.elapsedMs,
                    mistakes = s.counters.mistakes,
                    hints1 = s.counters.hints1,
                    hints2 = s.counters.hints2,
                    hints3 = s.counters.hints3,
                    clean = s.counters.hints3 == 0,
                    gridSummary = GameState.gridSummary(s),
                    boxFlags = s.boxFlags,
                )
            }
        }
        true
    } catch (e: SQLiteException) { // SQLiteFullException, SQLiteDiskIOException, ...
        android.util.Log.w(TAG, "Save failed: ${e.javaClass.simpleName}")
        false
    }

    /** US-002: the lowest unplayed index, else the least-recently-played puzzle (never blocked). */
    private suspend fun pickIndex(level: Level): Int {
        val count = source.count(Pool.NEWGAME, level)
        val played = dao.playedSet(level.ordinal).mapNotNull { row ->
            runCatching { PuzzleId.parse(row.puzzleId) }.getOrNull()?.takeIf { it.pool == Pool.NEWGAME }?.let { it.index to row.lastStarted }
        }.toMap()
        for (i in 0 until count) if (i !in played) return i
        return played.entries.filter { it.key < count }.minWith(compareBy({ it.value }, { it.key })).key
    }

    private fun today(): Long = LocalDate.now(clock.withZone(zone())).toEpochDay()

    private fun ContinueRow.toSummary() =
        ContinueSummary(id, Level.entries[level], kind == GameEntity.KIND_DAILY, elapsedMs, filledCount, gridSummary)

    companion object {
        private const val TAG = "GameRepository"
    }
}
