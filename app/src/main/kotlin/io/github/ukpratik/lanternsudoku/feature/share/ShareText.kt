// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.share

import android.content.Context
import android.content.Intent
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.data.ShareFacts
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.ui.components.formatElapsed
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The spoiler-free daily share text (US-017, LLD §4.13, design `completion-daily-share` and §9). A pure function:
 *
 * ```
 * Lantern Sudoku · Daily 15 Oct 2026 · Medium · 8:41 · 1 hint · no mistakes
 * 🟨🟨🟨
 * 🟨🟨🟨
 * 🟨💡🟨
 * ```
 * One square per box in reading order: 🟨 solved with no help, 💡 a hint pointed into the box, 🟧 a mistake was made
 * (and fixed) there; 💡 wins over 🟧. Exactly 9 emoji, no digits from the grid, no URL. The brand line is the same in
 * every locale (the brand and the format are fixed by design), so it isn't translated.
 */
object ShareText {
    const val SOLVED = "🟨" // 🟨 large yellow square
    const val HINT = "💡" // 💡 electric light bulb
    const val MISTAKE = "🟧" // 🟧 large orange square

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun build(f: ShareFacts): String = buildString {
        append("Lantern Sudoku · Daily ").append(f.date.format(DATE))
        append(" · ").append(levelName(f.level))
        append(" · ").append(formatElapsed(f.elapsedMs))
        append(" · ").append(count(f.hints, "hint", "hints"))
        append(" · ").append(count(f.mistakes, "mistake", "mistakes"))
        for (row in 0 until 3) {
            append('\n')
            for (col in 0 until 3) append(emoji(f.boxFlags, row * 3 + col))
        }
    }

    /** The square for [box] (0–8, reading order) from the 2-bit-per-box flags (HINT, then MISTAKE; BoxFlags). */
    fun emoji(boxFlags: Int, box: Int): String = when {
        boxFlags and (1 shl (2 * box)) != 0 -> HINT
        boxFlags and (1 shl (2 * box + 1)) != 0 -> MISTAKE
        else -> SOLVED
    }

    private fun count(n: Int, one: String, many: String) = when (n) {
        0 -> "no $many"
        1 -> "1 $one"
        else -> "$n $many"
    }

    private fun levelName(level: Level) = when (level) {
        Level.EASY -> "Easy"
        Level.MEDIUM -> "Medium"
        Level.HARD -> "Hard"
        Level.EXPERT -> "Expert"
    }

    /** ACTION_SEND, text/plain, in a chooser titled "Share your result". No permission, no network, no image. */
    fun intent(context: Context, f: ShareFacts): Intent {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, build(f))
        return Intent.createChooser(send, context.getString(R.string.share_sheet_title))
    }

    fun share(context: Context, f: ShareFacts) {
        context.startActivity(intent(context, f))
    }
}
