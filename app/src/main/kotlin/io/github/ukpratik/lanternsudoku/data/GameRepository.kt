// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.data

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import io.github.ukpratik.lanternsudoku.data.db.ContinueRow
import io.github.ukpratik.lanternsudoku.data.db.DailyRow
import io.github.ukpratik.lanternsudoku.data.db.LevelStatsRow
import io.github.ukpratik.lanternsudoku.engine.daily.DailyMapper
import io.github.ukpratik.lanternsudoku.engine.daily.Streaks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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

/**
 * Home's view of a saved game (US-001): level, time, progress and the mini-board summary. [dailyDate] is the daily's
 * own date (it never changes, design `state-daily-date-changed`); null for New games.
 */
data class ContinueSummary(
    val gameId: Long,
    val level: Level,
    val daily: Boolean,
    val elapsedMs: Long,
    val filled: Int,
    val gridSummary: String,
    val dailyDate: LocalDate? = null,
)

sealed interface LoadResult {
    /** [dailyDate] is the daily's own date (null for New games); it never changes once started. */
    data class Ok(val state: GameState, val elapsedMs: Long, val level: Level, val dailyDate: LocalDate? = null) : LoadResult

    /** The save couldn't be read or migrated; the row is now DISCARDED (design `state-save-corrupt`). */
    data class Discarded(val reason: String) : LoadResult

    /** No such in-progress game (already completed or abandoned elsewhere). */
    data object Missing : LoadResult
}

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
    /** The local date for started/completed days and dailies; shared with the screens (and the `dbg_date` hook). */
    val today: Today = Today(clock, zone),
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
        require(id.pool == Pool.NEWGAME) { "Dailies are opened by date: openOrCreateDaily" }
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

    /**
     * Opens the daily for [date] (US-014): the existing row if it was started before (in progress), otherwise a new
     * row whose `daily_epoch_day` is [date] for good and whose `started_epoch_day` is the real local date now, so a
     * past daily started later never counts toward the streak (PRD v1.3 §5). Returns null when that daily is already
     * completed (there's nothing to resume). A set-aside (DISCARDED) daily can be started again. One row per date
     * (unique index, TC-135).
     */
    suspend fun openOrCreateDaily(date: LocalDate): Long? = withContext(io) {
        val ref = DailyMapper.forDate(date)
        val record = source.read(ref.id)
        db.withTransaction {
            val existing = dao.daily(date.toEpochDay())
            when (existing?.status) {
                GameEntity.IN_PROGRESS -> return@withTransaction existing.id
                GameEntity.COMPLETED -> return@withTransaction null
                null -> Unit
                else -> dao.deleteDiscarded(existing.id)
            }
            val state = GameState.new(ref.id.toString(), record.givens)
            val now = clock.millis()
            _discardNotice.value = false
            dao.insert(
                GameEntity(
                    puzzleId = ref.id.toString(),
                    kind = GameEntity.KIND_DAILY,
                    level = ref.level.ordinal,
                    dailyEpochDay = date.toEpochDay(),
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

    /**
     * Today and the 6 previous dates, oldest first (the Daily tab strip, D-028), each with its state by row:
     * done, started (in progress) or not played. Recomputed when [dates] (today) changes or a daily row changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun dailyWeek(dates: Flow<LocalDate>): Flow<List<DailyDay>> = dates.distinctUntilChanged().flatMapLatest { t ->
        val from = t.minusDays(6)
        dao.dailies(from.toEpochDay(), t.toEpochDay()).map { rows -> week(from, rows) }
    }

    /** Streak, best and days played as of [dates] (US-015). */
    fun dailyStats(dates: Flow<LocalDate>): Flow<DailyStats> =
        combine(dao.streakDays(), dao.daysPlayed(), dates.distinctUntilChanged()) { days, played, t -> dailyStats(days, played, t) }

    /** The Stats tab (US-019): per level (dailies in their level, D-019) and the daily block. */
    fun stats(dates: Flow<LocalDate>): Flow<StatsView> = combine(dao.levelStats(), dailyStats(dates)) { rows, daily ->
        StatsView(rows.associate { Level.entries[it.level] to it.toStats() }, daily)
    }

    /**
     * The completion summary (US-018) of a completed game, read back after its completing write: time, hints by
     * stage, mistakes, clean, the best-time comparison (clean solves at this level, excluding this game), and for a
     * daily its date, the updated streak line and the share facts. Null if the row isn't completed.
     */
    suspend fun completion(gameId: Long): CompletionSummary? = withContext(io) {
        val row = dao.get(gameId)?.takeIf { it.status == GameEntity.COMPLETED } ?: return@withContext null
        val previous = dao.bestCleanExcluding(row.level, row.id)
        val level = Level.entries[row.level]
        val date = row.dailyEpochDay?.let(LocalDate::ofEpochDay)
        CompletionSummary(
            gameId = row.id,
            level = level,
            elapsedMs = row.elapsedMs,
            hints1 = row.hints1,
            hints2 = row.hints2,
            hints3 = row.hints3,
            mistakes = row.mistakes,
            clean = row.clean,
            previousBestMs = previous,
            newBest = row.clean && (previous == null || row.elapsedMs < previous),
            dailyDate = date,
            daily = date?.let { dailyStats(dao.streakDays().first(), dao.daysPlayed().first(), today.refresh()) },
            share = date?.let { ShareFacts(it, level, row.elapsedMs, row.hints1 + row.hints2 + row.hints3, row.mistakes, row.boxFlags) },
        )
    }

    suspend fun abandon(gameId: Long) {
        withContext(io) { dao.abandon(gameId, clock.millis()) }
    }

    suspend fun load(gameId: Long): LoadResult = withContext(io) {
        val row = dao.get(gameId) ?: return@withContext LoadResult.Missing
        if (row.status != GameEntity.IN_PROGRESS) return@withContext LoadResult.Missing
        val blob = row.stateBlob ?: return@withContext discard(gameId, "no blob")
        when (val r = StateCodec.decode(blob)) {
            is DecodeResult.Ok -> LoadResult.Ok(r.state, row.elapsedMs, Level.entries[row.level], row.dailyEpochDay?.let(LocalDate::ofEpochDay))
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

    private fun today(): Long = today.refresh().toEpochDay()

    private fun ContinueRow.toSummary() = ContinueSummary(
        id, Level.entries[level], kind == GameEntity.KIND_DAILY, elapsedMs, filledCount, gridSummary, dailyEpochDay?.let(LocalDate::ofEpochDay),
    )

    companion object {
        private const val TAG = "GameRepository"

        /** The 7 dates from [from] (oldest first) with their rows' states (D-028: today + 6 previous). */
        internal fun week(from: LocalDate, rows: List<DailyRow>): List<DailyDay> {
            val byDay = rows.associateBy { it.dailyEpochDay }
            return (0L until 7L).map { n ->
                val date = from.plusDays(n)
                val level = DailyMapper.forDate(date).level
                val row = byDay[date.toEpochDay()]
                val state = when (row?.status) {
                    GameEntity.COMPLETED -> DayState.DONE
                    GameEntity.IN_PROGRESS -> DayState.STARTED
                    else -> DayState.NOT_PLAYED // no row, or a set-aside save
                }
                DailyDay(
                    date = date,
                    level = level,
                    state = state,
                    elapsedMs = row?.elapsedMs ?: 0,
                    gameId = row?.id?.takeIf { state != DayState.NOT_PLAYED },
                    share = row?.takeIf { state == DayState.DONE }?.let {
                        ShareFacts(date, level, it.elapsedMs, it.hints1 + it.hints2 + it.hints3, it.mistakes, it.boxFlags)
                    },
                )
            }
        }

        internal fun dailyStats(streakDays: List<Long>, daysPlayed: Int, today: LocalDate): DailyStats {
            val days = streakDays.toSet()
            return DailyStats(Streaks.current(days, today.toEpochDay()), Streaks.best(days), daysPlayed)
        }

        private fun LevelStatsRow.toStats() = LevelStats(started, completed, abandoned, clean, bestMs, totalMs)
    }
}
