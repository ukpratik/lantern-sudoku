// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.tools

import kotlin.system.exitProcess

/** Entry point: `lantern-generator generate|validate|report [args]`. Real implementations land in M1 (ADR-004). */
fun main(args: Array<String>) {
    exitProcess(run(args.toList(), ::println))
}

/** Runs a command and returns the process exit code. Separate from [main] so tests can call it. */
fun run(args: List<String>, out: (String) -> Unit): Int {
    val command = args.firstOrNull()
    val rest = args.drop(1)
    return when (command) {
        "generate" -> notYet("generate", out)
        "validate" -> validate(rest, out)
        "report" -> notYet("report", out)
        else -> {
            out(USAGE)
            if (command == null || command == "help" || command == "--help") 0 else 2
        }
    }
}

// Placeholder that passes: there is no bank to check until M1, and the CI step must already be wired.
private fun validate(args: List<String>, out: (String) -> Unit): Int {
    val dir = args.firstOrNull() ?: "bank"
    out("validate: no bank yet in '$dir' (the validator arrives in M1); nothing to check. OK")
    return 0
}

private fun notYet(name: String, out: (String) -> Unit): Int {
    out("$name: not implemented yet (M1)")
    return 1
}

internal const val USAGE = """Usage: lantern-generator <command> [args]
Commands:
  generate   generate the puzzle bank (M1)
  validate   validate the committed bank: format, uniqueness, grades (M1)
  report     print the grade distribution and a hand-check sample (M1)"""
