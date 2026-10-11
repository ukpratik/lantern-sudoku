// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.platform

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import com.google.android.play.core.review.testing.FakeReviewManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-025, play flavour (TC-190 with FakeReviewManager): the prompter requests and launches the flow, never throws. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayReviewPrompterTest {
    @Test
    fun requestsAndLaunchesTheReviewFlow() = runBlocking {
        val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val fake = FakeReviewManager(ApplicationProvider.getApplicationContext())
        PlayReviewPrompter(fake).launch(activity) // completes without error: request → launch
        assertThat(StoreReview.prompter(activity)).isInstanceOf(PlayReviewPrompter::class.java)
    }
}
