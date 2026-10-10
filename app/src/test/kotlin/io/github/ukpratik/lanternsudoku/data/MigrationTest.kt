// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.data.db.LanternDatabase
import io.github.ukpratik.lanternsudoku.engine.save.DecodeResult
import io.github.ukpratik.lanternsudoku.engine.save.StateCodec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * NFR-005 / TC-022 scaffold. Builds a database exactly as a shipped schema version left it, from the frozen
 * `app/schemas/<db>/<version>.json` (the same steps as Room's MigrationTestHelper, which can't read unit-test assets
 * under Robolectric), puts a row with the frozen v1 blob fixture in it, then opens it with the current code and every
 * migration. Room validates the identity hash and the schema on open, so any drift fails here. Each future
 * `Migration(n, n + 1)` adds its frozen schema to [SHIPPED].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fixture = File("../engine/src/test/resources/fixtures/state-v1-500moves.json").readText().trim()

    @Test
    fun everyShippedSchema_withAFrozenV1Blob_opensWithTheCurrentCode() = runBlocking<Unit> {
        for (version in SHIPPED) {
            val name = "migration-from-$version.db"
            createFromSchema(name, version).use { db ->
                db.execSQL(
                    "INSERT INTO games (id, puzzle_id, kind, level, daily_epoch_day, status, started_at, started_epoch_day, updated_at, " +
                        "completed_at, completed_epoch_day, elapsed_ms, hints1, hints2, hints3, mistakes, clean, filled_count, grid_summary, " +
                        "box_flags, state_version, state_blob) VALUES (7, 'N-M-0000', 0, 1, NULL, 0, 1, 20000, 2, NULL, NULL, 61000, 0, 0, 0, " +
                        "3, 0, 40, ?, 0, 1, ?)",
                    arrayOf<Any>(".".repeat(81), fixture),
                )
                db.execSQL(
                    "INSERT INTO games (id, puzzle_id, kind, level, daily_epoch_day, status, started_at, started_epoch_day, updated_at, " +
                        "completed_at, completed_epoch_day, elapsed_ms, hints1, hints2, hints3, mistakes, clean, filled_count, grid_summary, " +
                        "box_flags, state_version, state_blob) VALUES (8, 'D-E-0003', 1, 0, 20001, 1, 1, 20001, 2, 3, 20001, 400000, 1, 0, 0, " +
                        "0, 1, 81, ?, 1, NULL, NULL)",
                    arrayOf<Any>(".".repeat(81)),
                )
            }
            val room = Room.databaseBuilder(context, LanternDatabase::class.java, name).addMigrations(*LanternDatabase.ALL_MIGRATIONS).build()
            val inProgress = room.gameDao().get(7)!!
            assertThat(inProgress.elapsedMs).isEqualTo(61_000)
            assertThat(StateCodec.decode(inProgress.stateBlob!!)).isInstanceOf(DecodeResult.Ok::class.java)
            val completed = room.gameDao().get(8)!!
            assertThat(completed.clean).isTrue()
            assertThat(completed.dailyEpochDay).isEqualTo(20_001)
            room.close()
        }
    }

    private fun createFromSchema(name: String, version: Int): SQLiteDatabase {
        val schema = Json.parseToJsonElement(File(SCHEMAS, "$version.json").readText()).jsonObject.getValue("database").jsonObject
        assertThat(schema.getValue("version").jsonPrimitive.int).isEqualTo(version)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        for (entity in schema.getValue("entities").jsonArray) {
            val e = entity.jsonObject
            val table = e.getValue("tableName").jsonPrimitive.content
            db.execSQL(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            for (index in e["indices"]?.jsonArray.orEmpty()) {
                db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        for (q in schema.getValue("setupQueries").jsonArray) db.execSQL(q.jsonPrimitive.content)
        db.version = version
        return db
    }

    private companion object {
        val SCHEMAS = File("schemas/io.github.ukpratik.lanternsudoku.data.db.LanternDatabase")

        /** Every schema version that has shipped (frozen); 1 until the first schema change. */
        val SHIPPED = listOf(1)
    }
}
