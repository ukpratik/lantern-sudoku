// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.ui.components

import androidx.annotation.StringRes
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.engine.model.Level

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
