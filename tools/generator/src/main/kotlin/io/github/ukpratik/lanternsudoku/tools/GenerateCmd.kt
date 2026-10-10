// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.tools

import io.github.ukpratik.lanternsudoku.engine.bank.BankCodec
import io.github.ukpratik.lanternsudoku.engine.bank.FrozenPrefix
import io.github.ukpratik.lanternsudoku.engine.bank.Manifest
import io.github.ukpratik.lanternsudoku.engine.bank.ManifestFile
import io.github.ukpratik.lanternsudoku.engine.gen.GenConfig
import io.github.ukpratik.lanternsudoku.engine.gen.Generator
import java.io.File
import java.io.FileOutputStream

/**
 * `generate [dir] [--newgame N] [--only file] [--count file=N] [--threads T]`: **appends** records to each bank file up to
 * its target (New game N, default 2,000; dailies 520 × weekday slots). Existing records are never touched; index i is
 * always generated from its own seed, so a file built in several runs is byte-identical to one built in a single run.
 */
object GenerateCmd {
    fun run(args: List<String>, out: (String) -> Unit): Int {
        val opts = Options.parse(args)
        val dir = bankDir(opts.positional.firstOrNull())
        dir.mkdirs()
        val newgame = opts.int("newgame") ?: NEWGAME_TARGET
        val threads = opts.int("threads") ?: Runtime.getRuntime().availableProcessors()
        val only = opts.values("only").map { BankFile.byName(it) }
        val overrides = opts.values("count").associate { kv ->
            val (k, v) = kv.split('=', limit = 2)
            BankFile.byName(k) to v.toInt()
        }
        val cfg = GenConfig.V1
        val started = System.nanoTime()
        for (bf in BankFile.ALL) {
            if (only.isNotEmpty() && bf !in only) continue
            val target = overrides[bf] ?: bf.target(newgame)
            val have = bf.count(dir)
            if (have >= target) {
                out("${bf.name}: $have records, target $target: nothing to do")
                continue
            }
            val t0 = System.nanoTime()
            val n = target - have
            val results = parallelMap(n, threads) { k -> Generator.generate(bf.pool, bf.level, have + k, cfg) }
            FileOutputStream(bf.file(dir), true).use { os -> results.forEach { os.write(BankCodec.encode(it.record)) } }
            val attempts = results.sumOf { it.attempts.toLong() }
            val secs = (System.nanoTime() - t0) / 1e9
            out(
                "${bf.name}: +$n (now $target) in ${"%.1f".format(secs)} s; ${attempts} grids tried, " +
                    "yield ${"%.2f".format(100.0 * n / attempts)}% (worst record ${results.maxOf { it.attempts }} grids)",
            )
        }
        writeManifest(dir, cfg)
        out("generate: done in ${"%.1f".format((System.nanoTime() - started) / 1e9)} s on $threads threads")
        return 0
    }

    /** Rewrites manifest counts from the files, keeping every existing frozen entry. */
    fun writeManifest(dir: File, cfg: GenConfig = GenConfig.V1, freezeRelease: String? = null) {
        val mf = File(dir, MANIFEST)
        val old = if (mf.exists()) Manifest.parse(mf.readText()) else null
        val files = BankFile.ALL.mapNotNull { bf ->
            if (!bf.file(dir).exists()) return@mapNotNull null
            val count = bf.count(dir)
            val frozen = old?.file(bf.name)?.frozen.orEmpty().toMutableList()
            if (freezeRelease != null && count > (frozen.lastOrNull()?.count ?: 0)) {
                require(frozen.none { it.release == freezeRelease }) { "${bf.name} already has a frozen entry for $freezeRelease" }
                frozen += FrozenPrefix(freezeRelease, count, Manifest.sha256(bf.bytes(dir)))
            }
            ManifestFile(bf.name, bf.pool, bf.level, count, frozen)
        }
        mf.writeText(Manifest(Manifest.FORMAT, cfg.version, files).toJson())
    }
}

/** `freeze [dir] <release>`: records a frozen prefix (count + SHA-256) for every file that grew since its last freeze. */
object FreezeCmd {
    fun run(args: List<String>, out: (String) -> Unit): Int {
        val opts = Options.parse(args)
        if (opts.positional.size != 2) {
            out("usage: freeze <bank dir> <release>")
            return 2
        }
        val dir = bankDir(opts.positional[0])
        GenerateCmd.writeManifest(dir, freezeRelease = opts.positional[1])
        out("freeze: manifest updated for release ${opts.positional[1]}")
        return 0
    }
}

/** Tiny argument parser: positionals plus repeatable `--key value` options. */
class Options(val positional: List<String>, private val named: Map<String, List<String>>) {
    fun values(key: String): List<String> = named[key].orEmpty()
    fun value(key: String): String? = named[key]?.lastOrNull()
    fun int(key: String): Int? = value(key)?.toInt()

    companion object {
        fun parse(args: List<String>): Options {
            val pos = ArrayList<String>()
            val named = LinkedHashMap<String, MutableList<String>>()
            var i = 0
            while (i < args.size) {
                val a = args[i]
                if (a.startsWith("--")) {
                    require(i + 1 < args.size) { "Option $a needs a value" }
                    named.getOrPut(a.removePrefix("--")) { ArrayList() } += args[i + 1]
                    i += 2
                } else {
                    pos += a
                    i++
                }
            }
            return Options(pos, named)
        }
    }
}
