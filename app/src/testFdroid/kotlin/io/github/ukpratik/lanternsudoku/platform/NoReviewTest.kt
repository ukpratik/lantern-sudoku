// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.platform

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** US-025 / TC-191, fdroid flavour: the no-op prompter, and no Play Core class on the classpath. */
class NoReviewTest {
    @Test
    fun fdroidNeverAsks_andShipsNoPlayLibrary() {
        assertThat(StoreReview.prompter(android.content.ContextWrapper(null))).isSameInstanceAs(ReviewPrompter.NONE)
        assertThat(runCatching { Class.forName("com.google.android.play.core.review.ReviewManager") }.isFailure).isTrue()
    }
}
