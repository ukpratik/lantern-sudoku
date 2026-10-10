// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.data

import io.github.ukpratik.lanternsudoku.engine.bank.BankCodec
import io.github.ukpratik.lanternsudoku.engine.bank.BankRecord
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.engine.model.Pool
import io.github.ukpratik.lanternsudoku.engine.model.PuzzleId
import io.github.ukpratik.lanternsudoku.engine.model.bankFileName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads bank records from the APK assets (`bank/v1/<file>`; LLD §3.2, §4.6). Record i sits at byte 84 × i, so a read is
 * a skip plus one 84-byte read on the (compressed) asset stream. [open] is `AssetManager::open` in the app and a plain
 * file reader in tests.
 */
class PuzzleSource(private val open: (path: String) -> InputStream, private val io: CoroutineDispatcher) {
    private val counts = ConcurrentHashMap<String, Int>()

    suspend fun read(id: PuzzleId): BankRecord = withContext(io) {
        open(path(id.pool, id.level)).use { stream ->
            skipFully(stream, id.index.toLong() * BankCodec.RECORD)
            val bytes = stream.readNBytesCompat(BankCodec.RECORD)
            if (bytes.size != BankCodec.RECORD) throw IOException("Record $id is past the end of the bank")
            BankCodec.decode(bytes)
        }
    }

    /** Number of records in a file; cached after the first call. */
    suspend fun count(pool: Pool, level: Level): Int {
        val path = path(pool, level)
        counts[path]?.let { return it }
        val n = withContext(io) {
            open(path).use { stream ->
                var total = 0L
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val r = stream.read(buf)
                    if (r < 0) break
                    total += r
                }
                BankCodec.count(total)
            }
        }
        counts[path] = n
        return n
    }

    private fun path(pool: Pool, level: Level) = "bank/v1/${bankFileName(pool, level)}"

    private fun skipFully(stream: InputStream, n: Long) {
        var left = n
        while (left > 0) {
            val skipped = stream.skip(left)
            if (skipped > 0) {
                left -= skipped
            } else if (stream.read() >= 0) {
                left--
            } else {
                throw IOException("Unexpected end of bank file")
            }
        }
    }

    private fun InputStream.readNBytesCompat(n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = read(out, off, n - off)
            if (r < 0) break
            off += r
        }
        return if (off == n) out else out.copyOf(off)
    }
}
