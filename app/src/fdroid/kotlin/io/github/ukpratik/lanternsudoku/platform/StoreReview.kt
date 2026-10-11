// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.platform

import android.content.Context

/** fdroid flavour (US-025, ADR-008): never asks for a rating and ships no Play library (TC-191). */
object StoreReview {
    @Suppress("UNUSED_PARAMETER")
    fun prompter(context: Context): ReviewPrompter = ReviewPrompter.NONE
}
