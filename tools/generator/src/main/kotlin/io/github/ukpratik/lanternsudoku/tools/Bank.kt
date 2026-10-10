// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.tools

import io.github.ukpratik.lanternsudoku.engine.bank.BankCodec
import io.github.ukpratik.lanternsudoku.engine.bank.Manifest
import io.github.ukpratik.lanternsudoku.engine.daily.DailyMapper
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.engine.model.Pool
import io.github.ukpratik.lanternsudoku.engine.model.bankFileName
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** One bank file of `bank/v1/`. */
data class BankFile(val pool: Pool, val level: Level) {
    val name: String get() = bankFileName(pool, level)

    fun file(dir: File): File = File(dir, name)

    fun bytes(dir: File): ByteArray = file(dir).let { if (it.exists()) it.readBytes() else ByteArray(0) }

    fun count(dir: File): Int = file(dir).let { if (it.exists()) BankCodec.count(it.length()) else 0 }

    /** Records the 1.0 target needs: New game [newgameTarget], dailies 520 weeks × slots (ADR-005). */
    fun target(newgameTarget: Int): Int = if (pool == Pool.DAILY) DailyMapper.dailyCount(level) else newgameTarget

    companion object {
        val ALL: List<BankFile> = Manifest.CANONICAL.map { (p, l) -> BankFile(p, l) }

        fun byName(name: String): BankFile = ALL.firstOrNull { it.name == name || it.name == "$name.txt" }
            ?: throw IllegalArgumentException("Unknown bank file '$name'")
    }
}

const val NEWGAME_TARGET = 2000
const val MANIFEST = "manifest.json"

/** The bank directory argument, defaulting to `bank/v1` under the working directory. */
fun bankDir(arg: String?): File = File(arg ?: "bank/v1")

/** Runs [work] for each index in parallel on all cores, returning results in index order (determinism by assembly). */
fun <T> parallelMap(n: Int, threads: Int = Runtime.getRuntime().availableProcessors(), work: (Int) -> T): List<T> {
    if (n == 0) return emptyList()
    val pool = Executors.newFixedThreadPool(threads)
    try {
        val futures = (0 until n).map { i -> pool.submit(Callable { work(i) }) }
        return futures.map { it.get() }
    } finally {
        pool.shutdownNow()
    }
}
