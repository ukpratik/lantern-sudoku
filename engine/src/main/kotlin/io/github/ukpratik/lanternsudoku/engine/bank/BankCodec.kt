// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.bank

import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.engine.technique.Technique

/** One bank record: the givens and the hardest technique the grader needed (which fixes the level). */
data class BankRecord(val givens: Grid, val hardest: Technique) {
    val level: Level get() = hardest.level
}

/** Thrown for a malformed record; the validator reports it with the record's ID. */
class BankFormatException(message: String) : IllegalArgumentException(message)

/**
 * Bank record v1 (ADR-004): exactly 84 bytes = 81 cell chars (`1`–`9`, `.` empty) + space + technique code (`a`–`j`) + LF.
 * No header: record `i` sits at byte `84 × i`.
 */
object BankCodec {
    const val RECORD = 84

    fun encode(r: BankRecord): ByteArray {
        val bytes = ByteArray(RECORD)
        val text = r.givens.toString()
        for (i in 0 until 81) bytes[i] = text[i].code.toByte()
        bytes[81] = ' '.code.toByte()
        bytes[82] = r.hardest.code.code.toByte()
        bytes[83] = '\n'.code.toByte()
        return bytes
    }

    fun decode(bytes: ByteArray, offset: Int = 0): BankRecord {
        if (offset < 0 || offset + RECORD > bytes.size) throw BankFormatException("Record at $offset runs past the end (${bytes.size} bytes)")
        val cells = IntArray(81)
        for (i in 0 until 81) {
            cells[i] = when (val ch = bytes[offset + i].toInt().toChar()) {
                '.' -> 0
                in '1'..'9' -> ch - '0'
                else -> throw BankFormatException("Bad cell byte '${ch.code}' at position $i")
            }
        }
        if (bytes[offset + 81] != ' '.code.toByte()) throw BankFormatException("Expected a space at position 81")
        if (bytes[offset + 83] != '\n'.code.toByte()) throw BankFormatException("Expected LF at position 83")
        val code = bytes[offset + 82].toInt().toChar()
        val technique = Technique.entries.firstOrNull { it.code == code } ?: throw BankFormatException("Unknown technique code '$code'")
        return BankRecord(Grid(cells), technique)
    }

    /** Number of records in a file of [length] bytes; throws if it isn't a whole number of records. */
    fun count(length: Long): Int {
        if (length % RECORD != 0L) throw BankFormatException("File length $length is not a multiple of $RECORD")
        return (length / RECORD).toInt()
    }

    fun decodeAll(bytes: ByteArray): List<BankRecord> = List(count(bytes.size.toLong())) { decode(bytes, it * RECORD) }
}
