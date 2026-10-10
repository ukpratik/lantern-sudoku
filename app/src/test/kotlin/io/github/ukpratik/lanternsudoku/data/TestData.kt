// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.ukpratik.lanternsudoku.data.db.LanternDatabase
import io.github.ukpratik.lanternsudoku.engine.bank.BankCodec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

/** Shared test wiring: an in-memory Room database and a PuzzleSource over the committed bank (or its first records). */
internal object TestData {
    fun db(): LanternDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LanternDatabase::class.java).build()

    /** The real bank, read from `bank/v1/` (the same files the APK ships as assets). [limit] truncates every file. */
    fun source(io: CoroutineDispatcher = Dispatchers.IO, limit: Int? = null) = PuzzleSource({ path ->
        val file = File("../", path.replace("bank/v1/", "bank/v1/"))
        if (limit == null) FileInputStream(file) else ByteArrayInputStream(file.readBytes().copyOf(limit * BankCodec.RECORD))
    }, io)
}
