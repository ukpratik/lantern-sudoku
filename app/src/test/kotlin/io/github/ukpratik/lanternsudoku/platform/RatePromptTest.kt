// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.platform

import android.app.Activity
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-025 rules (TC-190), on both flavours: the policy and the coordinator that records the request. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RatePromptTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val day = 86_400_000L
    private val today = 20_100L
    private val now = today * day + 3_600_000L

    @Test
    fun tc190_policy() {
        fun ask(completed: Int, first: Long?, last: Long) = RatePolicy.shouldAsk(completed, today, first, last, now)
        assertThat(ask(4, today - 1, 0)).isFalse() // 4 completions
        assertThat(ask(5, today, 0)).isFalse() // 5, but on the first-launch day
        assertThat(ask(5, today - 1, 0)).isTrue() // 5 on day 2
        assertThat(ask(5, null, 0)).isFalse() // first launch unknown: be conservative
        assertThat(ask(9, today - 90, now - 59 * day)).isFalse() // 59 days after the last request
        assertThat(ask(9, today - 90, now - 60 * day)).isTrue() // 60 days after
    }

    @Test
    fun coordinatorRecordsTheRequestAndCallsTheStoreOnlyWhenAllowed() = runTest {
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { java.io.File(tmp.root, "s.preferences_pb") })
        var completed = 5
        var day0 = today
        var launches = 0
        val prompt = RatePrompt(settings, { completed }, { day0 }, { launches++ }, now = { day0 * day + 3_600_000L })
        val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()

        settings.recordFirstLaunch(today)
        assertThat(prompt.maybeAsk(activity)).isFalse() // first day
        day0 = today + 3
        completed = 4
        assertThat(prompt.maybeAsk(activity)).isFalse() // 4 completions
        completed = 5
        assertThat(prompt.maybeAsk(activity)).isTrue()
        assertThat(launches).isEqualTo(1)
        assertThat(settings.marks.first().lastReviewPromptAt).isEqualTo(day0 * day + 3_600_000L) // recorded, shown or not
        day0 = today + 62
        assertThat(prompt.maybeAsk(activity)).isFalse() // 59 days later
        day0 = today + 63
        assertThat(prompt.maybeAsk(activity)).isTrue() // 60 days later
        assertThat(launches).isEqualTo(2)
    }
}
