// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.tools

import io.github.ukpratik.lanternsudoku.engine.bank.BankCodec
import io.github.ukpratik.lanternsudoku.engine.bank.BankFormatException
import io.github.ukpratik.lanternsudoku.engine.bank.Manifest
import io.github.ukpratik.lanternsudoku.engine.daily.DailyMapper
import io.github.ukpratik.lanternsudoku.engine.gen.GenConfig
import io.github.ukpratik.lanternsudoku.engine.model.Pool
import io.github.ukpratik.lanternsudoku.engine.model.PuzzleId
import io.github.ukpratik.lanternsudoku.engine.solve.BacktrackingSolver
import io.github.ukpratik.lanternsudoku.engine.solve.Grader
import io.github.ukpratik.lanternsudoku.engine.solve.LogicalSolver
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * `validate [dir]` (ADR-004, LLD §8, TC-115 to TC-123): every rule of US-013 over every record. Exit code 1 on any
 * violation, listing up to 50 of them by puzzle ID.
 */
object ValidateCmd {
    const val MAX_COMPRESSED_BYTES = 1_000_000L

    fun run(args: List<String>, out: (String) -> Unit): Int {
        val opts = Options.parse(args)
        val dir = bankDir(opts.positional.firstOrNull())
        val started = System.nanoTime()
        val errors = validate(dir, opts.int("threads") ?: Runtime.getRuntime().availableProcessors(), out)
        val secs = (System.nanoTime() - started) / 1e9
        errors.take(50).forEach { out("  FAIL $it") }
        if (errors.size > 50) out("  … and ${errors.size - 50} more")
        val verdict = if (errors.isEmpty()) "OK" else "FAILED (${errors.size} problems)"
        out("validate: $verdict in ${"%.1f".format(secs)} s")
        return if (errors.isEmpty()) 0 else 1
    }

    /** Returns every problem found (empty = valid). */
    fun validate(dir: File, threads: Int, out: (String) -> Unit): List<String> {
        val errors = ArrayList<String>()
        if (!dir.isDirectory) return listOf("bank directory '$dir' does not exist")
        val manifestFile = File(dir, MANIFEST)
        if (!manifestFile.exists()) return listOf("$MANIFEST is missing in '$dir'")
        val manifest = try {
            Manifest.parse(manifestFile.readText())
        } catch (e: Exception) {
            return listOf("$MANIFEST does not parse: ${e.message}")
        }
        if (manifest.format != Manifest.FORMAT) errors += "manifest format ${manifest.format}, expected ${Manifest.FORMAT}"
        if (manifest.genConfig != GenConfig.V1.version) errors += "manifest genConfig ${manifest.genConfig}, expected ${GenConfig.V1.version}"
        if (manifest.files.map { it.name } != Manifest.canonicalNames()) {
            errors += "manifest files ${manifest.files.map { it.name }} != expected ${Manifest.canonicalNames()}"
        }
        val extra = dir.listFiles().orEmpty().map { it.name }.filter { it.endsWith(".txt") && it !in Manifest.canonicalNames() }
        if (extra.isNotEmpty()) errors += "unexpected bank files: $extra"

        val seen = HashMap<String, PuzzleId>()
        var total = 0
        val compressed = ByteArrayOutputStream()
        DeflaterOutputStream(compressed, Deflater(Deflater.DEFAULT_COMPRESSION)).use { z ->
            for (bf in BankFile.ALL) {
                val f = bf.file(dir)
                if (!f.exists()) {
                    errors += "${bf.name} is missing"
                    continue
                }
                val bytes = f.readBytes()
                z.write(bytes)
                val count = try {
                    BankCodec.count(bytes.size.toLong())
                } catch (e: BankFormatException) {
                    errors += "${bf.name}: ${e.message}"
                    continue
                }
                val entry = manifest.file(bf.name)
                if (entry != null) {
                    if (entry.pool != bf.pool || entry.level != bf.level) errors += "${bf.name}: manifest pool/level ${entry.pool}/${entry.level}"
                    if (entry.count != count) errors += "${bf.name}: manifest count ${entry.count} != file count $count"
                    var prev = 0
                    for (z0 in entry.frozen) {
                        when {
                            z0.count < prev -> errors += "${bf.name}: frozen ${z0.release} count ${z0.count} shrinks below $prev"
                            z0.count > count -> errors += "${bf.name}: frozen ${z0.release} covers ${z0.count} records but the file has $count (records removed)"
                            Manifest.sha256(bytes, z0.count * BankCodec.RECORD) != z0.sha256 ->
                                errors += "${bf.name}: frozen prefix ${z0.release} (${z0.count} records) SHA-256 mismatch: a shipped record was edited"
                        }
                        prev = z0.count
                    }
                }
                if (bf.pool == Pool.DAILY && count < DailyMapper.dailyCount(bf.level)) {
                    errors += "${bf.name}: $count records, the daily mapping needs ${DailyMapper.dailyCount(bf.level)}"
                }
                if (count == 0) errors += "${bf.name}: empty"
                val problems = parallelMap(count, threads) { i -> checkRecord(bf, i, bytes) }
                problems.forEach { if (it != null) errors += it }
                for (i in 0 until count) {
                    val id = PuzzleId(bf.pool, bf.level, i)
                    val key = String(bytes, i * BankCodec.RECORD, 81, Charsets.US_ASCII)
                    seen.put(key, id)?.let { errors += "$id duplicates $it" }
                }
                total += count
                out("  ${bf.name}: $count records checked")
            }
        }
        if (compressed.size() > MAX_COMPRESSED_BYTES) errors += "bank compresses to ${compressed.size()} bytes, over $MAX_COMPRESSED_BYTES (NFR-003)"
        out("  $total records; compressed bank ${compressed.size() / 1024} KB (limit ${MAX_COMPRESSED_BYTES / 1000} KB)")
        return errors
    }

    /** All per-record rules. Returns a problem description or null. */
    internal fun checkRecord(bf: BankFile, index: Int, bytes: ByteArray): String? {
        val id = PuzzleId(bf.pool, bf.level, index)
        val record = try {
            BankCodec.decode(bytes, index * BankCodec.RECORD)
        } catch (e: BankFormatException) {
            return "$id: ${e.message}"
        }
        val givens = record.givens
        if (record.level != bf.level) return "$id: code '${record.hardest.code}' (${record.hardest}) is ${record.level}, file is ${bf.level}"
        GenConfig.V1.givensWindow(bf.level)?.let { w ->
            if (givens.filledCount !in w) return "$id: ${givens.filledCount} givens, ${bf.level} needs ${w.first}–${w.last}"
        }
        val dlx = DlxCounter().count(givens, 2)
        if (dlx.count != 1) return "$id: DLX counts ${dlx.count} solutions"
        val bt = BacktrackingSolver.countSolutions(givens, 2)
        if (bt != 1) return "$id: backtracking counts $bt solutions"
        if (BacktrackingSolver.solve(givens) != dlx.firstSolution) return "$id: backtracking and DLX solutions differ"
        val restricted = LogicalSolver.forLevel(bf.level).solve(givens)
        if (!restricted.solved) return "$id: not solvable with ${bf.level} techniques (stuck after ${restricted.steps.size} steps)"
        if (restricted.grid != dlx.firstSolution) return "$id: logical solution differs from the DLX solution"
        val grade = Grader.grade(givens)
        if (grade.hardest != record.hardest) return "$id: re-grade ${grade.hardest} != stored ${record.hardest}"
        return null
    }
}
