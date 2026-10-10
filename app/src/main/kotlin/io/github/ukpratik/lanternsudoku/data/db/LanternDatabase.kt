// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * `lantern.db` (LLD §2.1, ADR-006). Version 1 at 1.0. Explicit migrations only, never a destructive fallback; every
 * shipped schema stays frozen in `app/schemas/` and gets a migration test to the current version.
 */
@Database(entities = [GameEntity::class], version = 1, exportSchema = true)
abstract class LanternDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao

    companion object {
        const val NAME = "lantern.db"

        /** One `Migration(n, n + 1)` per schema change, each with a test (`MigrationTest`). Empty at version 1. */
        val ALL_MIGRATIONS: Array<Migration> = emptyArray()

        fun create(context: Context): LanternDatabase =
            Room.databaseBuilder(context, LanternDatabase::class.java, NAME)
                // One consistent file for Auto Backup and device transfer (ADR-014).
                .setJournalMode(JournalMode.TRUNCATE)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
