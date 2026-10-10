// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.tools

import kotlin.system.exitProcess

/** Entry point: `lantern-generator generate|validate|report|freeze [args]` (ADR-004). */
fun main(args: Array<String>) {
    exitProcess(run(args.toList(), ::println))
}

/** Runs a command and returns the process exit code. Separate from [main] so tests can call it. */
fun run(args: List<String>, out: (String) -> Unit): Int {
    val command = args.firstOrNull()
    val rest = args.drop(1)
    return try {
        when (command) {
            "generate" -> GenerateCmd.run(rest, out)
            "validate" -> ValidateCmd.run(rest, out)
            "report" -> ReportCmd.run(rest, out)
            "freeze" -> FreezeCmd.run(rest, out)
            else -> {
                out(USAGE)
                if (command == null || command == "help" || command == "--help") 0 else 2
            }
        }
    } catch (e: IllegalArgumentException) {
        out("$command: ${e.message}")
        2
    }
}

internal const val USAGE = """Usage: lantern-generator <command> [args]
Commands:
  generate [dir] [--newgame N] [--only FILE] [--count FILE=N] [--threads T]
             append generated records up to each file's target (New game 2,000; dailies 1,040/1,040/1,040/520)
  validate [dir]   validate the committed bank: format, two independent solution counters, logical solve within
             the level, re-grade, givens windows, duplicates, manifest and frozen-prefix SHA-256s, compressed size
  report [dir] [--out FILE] [--sample N]
             print the grade distribution and hint-search statistics; write the hand-check sample (N per level)
  freeze <dir> <release>
             record a frozen prefix (count + SHA-256) for every file that grew since its last freeze
The bank directory defaults to bank/v1."""
