// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.ui.components

import android.content.res.Resources
import androidx.annotation.StringRes
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.engine.model.Level
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Thursday 15 October" (greeting, TalkBack). The app is English-only in 1.0; day-month order as in the design. */
val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)

/** "Thu 15 Oct" (Today row, Continue card, completion eyebrow). */
val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** "Thursday" (Daily card title). */
val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)

/** "F" (strip weekday initial). */
val WEEKDAY_INITIAL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEEE", Locale.ENGLISH)

/** Play time for TalkBack: "21 minutes 6 seconds", "1 hour 2 minutes 3 seconds" (design completion). */
fun spokenElapsed(res: Resources, ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return buildList {
        if (h > 0) add(res.getQuantityString(R.plurals.spoken_hours, h, h))
        if (m > 0 || h > 0) add(res.getQuantityString(R.plurals.spoken_minutes, m, m))
        add(res.getQuantityString(R.plurals.spoken_seconds, s, s))
    }.joinToString(" ")
}

/** Play time as m:ss, or h:mm:ss from one hour (design §9). */
fun formatElapsed(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@get:StringRes
val Level.label: Int
    get() = when (this) {
        Level.EASY -> R.string.level_easy
        Level.MEDIUM -> R.string.level_medium
        Level.HARD -> R.string.level_hard
        Level.EXPERT -> R.string.level_expert
    }

@get:StringRes
val Level.summary: Int
    get() = when (this) {
        Level.EASY -> R.string.level_summary_easy
        Level.MEDIUM -> R.string.level_summary_medium
        Level.HARD -> R.string.level_summary_hard
        Level.EXPERT -> R.string.level_summary_expert
    }
