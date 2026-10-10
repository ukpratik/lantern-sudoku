// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.data

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.data.db.GameEntity
import io.github.ukpratik.lanternsudoku.data.db.LanternDatabase
import io.github.ukpratik.lanternsudoku.engine.game.Action
import io.github.ukpratik.lanternsudoku.engine.game.GameReducer
import io.github.ukpratik.lanternsudoku.engine.game.Rules
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.engine.model.Pool
import io.github.ukpratik.lanternsudoku.engine.model.PuzzleId
import io.github.ukpratik.lanternsudoku.engine.save.StateCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** US-002 puzzle selection, US-003 saves (incl. TC-019 failure and TC-023/024 discard), invariants, DAO queries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GameRepositoryTest {
    private val db: LanternDatabase = TestData.db()
    private var millis = 1_760_000_000_000L
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(millis)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun repo(limit: Int? = null) = GameRepository(db, TestData.source(limit = limit), Dispatchers.IO, clock, { ZoneOffset.UTC })

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test
    fun TC007_TC008_newGameNeverRepeatsAPlayedPuzzleWhileUnplayedOnesExist() = runBlocking<Unit> {
        val r = repo()
        val seen = mutableSetOf<String>()
        repeat(50) {
            millis += 1_000
            val id = r.startNew(Level.EASY)
            val row = db.gameDao().get(id)!!
            assertThat(row.puzzleId).startsWith("N-E-")
            assertThat(seen.add(row.puzzleId)).isTrue()
            r.abandon(id)
        }
    }

    @Test
    fun TC013_exhaustedLevel_offersTheLeastRecentlyPlayed_neverBlocked() = runBlocking<Unit> {
        val r = repo(limit = 3)
        val ids = (0 until 3).map {
            millis += 1_000
            r.startNew(Level.HARD).also { id -> r.abandon(id) }
        }
        assertThat(ids.map { db.gameDao().get(it)!!.puzzleId }).containsExactly("N-H-0000", "N-H-0001", "N-H-0002").inOrder()
        millis += 1_000
        val again = r.startNew(Level.HARD)
        assertThat(db.gameDao().get(again)!!.puzzleId).isEqualTo("N-H-0000")
    }

    @Test
    fun TC012_onePerLevel_startNewReturnsTheSavedGame_unlessAbandoning() = runBlocking<Unit> {
        val r = repo()
        val first = r.startNew(Level.MEDIUM)
        assertThat(r.startNew(Level.MEDIUM)).isEqualTo(first)
        val easy = r.startNew(Level.EASY)
        assertThat(easy).isNotEqualTo(first)
        val replaced = r.startNew(Level.MEDIUM, abandonExisting = true)
        assertThat(replaced).isNotEqualTo(first)
        assertThat(db.gameDao().get(first)!!.status).isEqualTo(GameEntity.ABANDONED)
        assertThat(db.gameDao().get(first)!!.stateBlob).isNull()
        assertThat(r.inProgressLevels().first()).containsExactly(Level.EASY, Level.MEDIUM)
    }

    @Test
    fun saveAndLoad_roundTripsTheBoard_andHomeReadsOnlySummaryColumns() = runBlocking<Unit> {
        val r = repo()
        val id = r.startNew(Level.EASY)
        var s = (r.load(id) as LoadResult.Ok).state
        val cell = (0 until 81).first { !s.isGiven(it) }
        s = GameReducer.reduce(s, Action.TapCell(cell), Rules()).state
        s = GameReducer.reduce(s, Action.TapDigit(s.solution[cell]), Rules()).state
        assertThat(r.write(SaveRequest.Full(id, s, 12_345))).isTrue()
        val loaded = r.load(id) as LoadResult.Ok
        assertThat(loaded.state.codes()).isEqualTo(s.codes())
        assertThat(loaded.state.undo).isEqualTo(s.undo)
        assertThat(loaded.elapsedMs).isEqualTo(12_345)
        val card = r.continueCard().first()!!
        assertThat(card.gameId).isEqualTo(id)
        assertThat(card.filled).isEqualTo(s.filledCount)
        assertThat(card.gridSummary[cell]).isEqualTo('a' + (s.solution[cell] - 1))
    }

    @Test
    fun TC019_failedWrite_keepsThePreviousSaveIntact_andReportsOnce() = runBlocking<Unit> {
        val r = repo()
        val id = r.startNew(Level.EASY)
        val s0 = (r.load(id) as LoadResult.Ok).state
        val cell = (0 until 81).first { !s0.isGiven(it) }
        val s1 = GameReducer.reduce(GameReducer.reduce(s0, Action.TapCell(cell), Rules()).state, Action.TapDigit(1), Rules()).state
        val s2 = GameReducer.reduce(s1, Action.TapDigit(2), Rules()).state
        val queue = SaveQueue(scope, r, Dispatchers.IO)
        queue.offer(SaveRequest.Full(id, s1, 1_000))
        queue.flush()
        val before = db.gameDao().get(id)!!
        // Storage refuses the next write (a real SQLite error; the UPDATE rolls back).
        db.openHelper.writableDatabase.query("PRAGMA query_only = ON").close()
        queue.offer(SaveRequest.Full(id, s2, 2_000))
        queue.flush()
        assertThat(queue.lastWriteOk.value).isFalse()
        db.openHelper.writableDatabase.query("PRAGMA query_only = OFF").close()
        assertThat(db.gameDao().get(id)).isEqualTo(before)
        // The next change retries naturally and succeeds.
        queue.offer(SaveRequest.Full(id, s2, 3_000))
        queue.flush()
        assertThat(queue.lastWriteOk.value).isTrue()
        assertThat((r.load(id) as LoadResult.Ok).state.codes()).isEqualTo(s2.codes())
    }

    @Test
    fun TC023_TC024_unreadableSave_isDiscarded_otherRowsUntouched_noticeRaised() = runBlocking<Unit> {
        val r = repo()
        val done = r.startNew(Level.HARD)
        val doneState = (r.load(done) as LoadResult.Ok).state
        r.write(SaveRequest.Complete(done, doneState, 60_000))
        val corrupt = r.startNew(Level.EASY)
        val future = r.startNew(Level.MEDIUM)
        db.openHelper.writableDatabase.execSQL("UPDATE games SET state_blob = '{\"v\":1,\"x\":' WHERE id = $corrupt")
        val futureBlob = db.gameDao().get(future)!!.stateBlob!!.replace("\"v\":1", "\"v\":99")
        db.openHelper.writableDatabase.execSQL("UPDATE games SET state_blob = ? WHERE id = $future", arrayOf(futureBlob))

        assertThat(r.verifyInProgress()).isEqualTo(2)
        assertThat(r.discardNotice.value).isTrue()
        for (id in listOf(corrupt, future)) {
            val row = db.gameDao().get(id)!!
            assertThat(row.status).isEqualTo(GameEntity.DISCARDED)
            assertThat(row.stateBlob).isNull()
            assertThat(r.load(id)).isEqualTo(LoadResult.Missing)
        }
        assertThat(db.gameDao().get(done)!!.status).isEqualTo(GameEntity.COMPLETED) // stats are safe
        assertThat(r.continueCard().first()).isNull()
        // Starting any game dismisses the notice.
        r.startNew(Level.EXPERT)
        assertThat(r.discardNotice.value).isFalse()
    }

    /** ISS-006: a state_blob stored as an SQLite BLOB can't be read as text; it must not crash, only that row is set aside. */
    @Test
    fun ISS006_blobTypedSave_isDiscardedLikeACorruptOne_withoutCrashing() = runBlocking<Unit> {
        val r = repo()
        val done = r.startNew(Level.HARD)
        r.write(SaveRequest.Complete(done, (r.load(done) as LoadResult.Ok).state, 60_000))
        val good = r.startNew(Level.MEDIUM)
        val blobTyped = r.startNew(Level.EASY)
        db.openHelper.writableDatabase.execSQL(
            "UPDATE games SET state_blob = CAST('{\"v\":1,\"x\":' AS BLOB) WHERE id = $blobTyped",
        )
        db.openHelper.writableDatabase.query("SELECT typeof(state_blob) FROM games WHERE id = $blobTyped").use {
            it.moveToFirst()
            assertThat(it.getString(0)).isEqualTo("blob")
        }

        assertThat(r.verifyInProgress()).isEqualTo(1)
        assertThat(r.discardNotice.value).isTrue()
        assertThat(db.gameDao().get(blobTyped)!!.status).isEqualTo(GameEntity.DISCARDED)
        assertThat(r.load(good)).isInstanceOf(LoadResult.Ok::class.java)
        assertThat(db.gameDao().get(done)!!.status).isEqualTo(GameEntity.COMPLETED) // stats are safe
    }

    /** ISS-006: opening such a row directly (before the start-up check ran) also takes the corrupt-save path. */
    @Test
    fun ISS006_loadingABlobTypedSave_discardsIt() = runBlocking<Unit> {
        val r = repo()
        val id = r.startNew(Level.EASY)
        db.openHelper.writableDatabase.execSQL("UPDATE games SET state_blob = CAST('{\"v\":1}' AS BLOB) WHERE id = $id")
        assertThat(r.load(id)).isInstanceOf(LoadResult.Discarded::class.java)
        assertThat(r.discardNotice.value).isTrue()
        assertThat(db.gameDao().get(id)!!.status).isEqualTo(GameEntity.DISCARDED)
    }

    @Test
    fun completion_dropsTheBlob_andAStaleSaveCannotResurrectIt() = runBlocking<Unit> {
        val r = repo()
        val id = r.startNew(Level.EASY)
        val s = (r.load(id) as LoadResult.Ok).state
        r.write(SaveRequest.Complete(id, s, 90_000))
        r.write(SaveRequest.Full(id, s, 95_000)) // late autosave
        val row = db.gameDao().get(id)!!
        assertThat(row.status).isEqualTo(GameEntity.COMPLETED)
        assertThat(row.stateBlob).isNull()
        assertThat(row.elapsedMs).isEqualTo(90_000)
        assertThat(row.clean).isTrue()
        assertThat(row.completedEpochDay).isNotNull()
    }

    @Test(expected = SQLiteConstraintException::class)
    fun TC135_onlyOneRowPerDailyDate() = runBlocking<Unit> {
        val dao = db.gameDao()
        val blob = StateCodec.encode(io.github.ukpratik.lanternsudoku.engine.game.GameState.new("D-E-0000",
            TestData.source().read(PuzzleId(Pool.DAILY, Level.EASY, 0)).givens))
        fun daily() = GameEntity(puzzleId = "D-E-0000", kind = GameEntity.KIND_DAILY, level = 0, dailyEpochDay = 20_000, status = 0,
            startedAt = 0, startedEpochDay = 20_000, updatedAt = 0, filledCount = 0, gridSummary = ".".repeat(81), stateVersion = 1, stateBlob = blob)
        dao.insert(daily())
        dao.insert(daily())
    }

    @Test
    fun TC122_newGameOnlyServesNewGameIds() = runBlocking<Unit> {
        val r = repo()
        repeat(4) { k -> assertThat(db.gameDao().get(r.startNew(Level.entries[k]))!!.puzzleId).startsWith("N-") }
    }
}
