// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.bank

import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.engine.model.Pool
import io.github.ukpratik.lanternsudoku.engine.model.bankFileName
import java.security.MessageDigest

/** A frozen prefix (ADR-004): the first [count] records as shipped in [release], with the SHA-256 of their bytes. */
data class FrozenPrefix(val release: String, val count: Int, val sha256: String)

data class ManifestFile(val name: String, val pool: Pool, val level: Level, val count: Int, val frozen: List<FrozenPrefix>)

/**
 * `bank/v1/manifest.json` (LLD §4.6). Written and read here with a tiny strict JSON reader, so `:engine` needs no
 * serialization library for M1. Files are always written in canonical order (pool, then level).
 */
data class Manifest(val format: Int, val genConfig: Int, val files: List<ManifestFile>) {
    fun file(name: String): ManifestFile? = files.firstOrNull { it.name == name }

    fun toJson(): String = buildString {
        append("{\n  \"format\": ").append(format).append(",\n  \"genConfig\": ").append(genConfig).append(",\n  \"files\": {\n")
        files.forEachIndexed { i, f ->
            append("    \"").append(f.name).append("\": {\n")
            append("      \"pool\": \"").append(f.pool.name).append("\",\n")
            append("      \"level\": \"").append(f.level.name).append("\",\n")
            append("      \"count\": ").append(f.count).append(",\n")
            append("      \"frozen\": [")
            if (f.frozen.isEmpty()) append("]") else {
                append('\n')
                f.frozen.forEachIndexed { j, z ->
                    append("        { \"release\": \"").append(z.release).append("\", \"count\": ").append(z.count)
                    append(", \"sha256\": \"").append(z.sha256).append("\" }")
                    append(if (j < f.frozen.size - 1) ",\n" else "\n")
                }
                append("      ]")
            }
            append("\n    }").append(if (i < files.size - 1) ",\n" else "\n")
        }
        append("  }\n}\n")
    }

    companion object {
        const val FORMAT = 1

        /** Canonical file order: New game easy…expert, then Daily easy…expert. */
        val CANONICAL: List<Pair<Pool, Level>> = Pool.entries.flatMap { p -> Level.entries.map { p to it } }

        fun canonicalNames(): List<String> = CANONICAL.map { (p, l) -> bankFileName(p, l) }

        @Suppress("UNCHECKED_CAST")
        fun parse(json: String): Manifest {
            val root = JsonReader(json).readDocument() as Map<String, Any?>
            val files = (root["files"] as? Map<String, Any?>) ?: error("manifest: missing files")
            return Manifest(
                format = (root["format"] as? Long)?.toInt() ?: error("manifest: missing format"),
                genConfig = (root["genConfig"] as? Long)?.toInt() ?: error("manifest: missing genConfig"),
                files = files.map { (name, v) ->
                    val o = v as Map<String, Any?>
                    ManifestFile(
                        name = name,
                        pool = Pool.valueOf(o["pool"] as String),
                        level = Level.valueOf(o["level"] as String),
                        count = (o["count"] as Long).toInt(),
                        frozen = (o["frozen"] as List<Any?>).map { z ->
                            val m = z as Map<String, Any?>
                            FrozenPrefix(m["release"] as String, (m["count"] as Long).toInt(), m["sha256"] as String)
                        },
                    )
                },
            )
        }

        fun sha256(bytes: ByteArray, length: Int = bytes.size): String {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(bytes, 0, length)
            return md.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }
    }
}

/** Minimal strict JSON reader: objects (insertion-ordered), arrays, strings (no escapes but \" and \\), integers, true/false/null. */
private class JsonReader(private val s: String) {
    private var i = 0

    fun readDocument(): Any? {
        val v = value()
        ws()
        require(i == s.length) { "JSON: trailing content at $i" }
        return v
    }

    private fun ws() {
        while (i < s.length && s[i].isWhitespace()) i++
    }

    private fun value(): Any? {
        ws()
        require(i < s.length) { "JSON: unexpected end" }
        return when (val ch = s[i]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> str()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> if (ch == '-' || ch.isDigit()) num() else throw IllegalArgumentException("JSON: unexpected '$ch' at $i")
        }
    }

    private fun literal(word: String, v: Any?): Any? {
        require(s.startsWith(word, i)) { "JSON: bad literal at $i" }
        i += word.length
        return v
    }

    private fun num(): Long {
        val start = i
        if (s[i] == '-') i++
        while (i < s.length && s[i].isDigit()) i++
        return s.substring(start, i).toLong()
    }

    private fun str(): String {
        i++ // opening quote
        val sb = StringBuilder()
        while (true) {
            require(i < s.length) { "JSON: unterminated string" }
            val ch = s[i++]
            when (ch) {
                '"' -> return sb.toString()
                '\\' -> {
                    val e = s[i++]
                    require(e == '"' || e == '\\' || e == '/') { "JSON: unsupported escape \\$e" }
                    sb.append(e)
                }
                else -> sb.append(ch)
            }
        }
    }

    private fun arr(): List<Any?> {
        i++
        val out = ArrayList<Any?>()
        ws()
        if (s[i] == ']') { i++; return out }
        while (true) {
            out += value()
            ws()
            when (s[i++]) {
                ',' -> continue
                ']' -> return out
                else -> throw IllegalArgumentException("JSON: expected , or ] at ${i - 1}")
            }
        }
    }

    private fun obj(): Map<String, Any?> {
        i++
        val out = LinkedHashMap<String, Any?>()
        ws()
        if (s[i] == '}') { i++; return out }
        while (true) {
            ws()
            require(s[i] == '"') { "JSON: expected a key at $i" }
            val k = str()
            ws()
            require(s[i++] == ':') { "JSON: expected : at ${i - 1}" }
            require(k !in out) { "JSON: duplicate key '$k'" }
            out[k] = value()
            ws()
            when (s[i++]) {
                ',' -> continue
                '}' -> return out
                else -> throw IllegalArgumentException("JSON: expected , or } at ${i - 1}")
            }
        }
    }
}
