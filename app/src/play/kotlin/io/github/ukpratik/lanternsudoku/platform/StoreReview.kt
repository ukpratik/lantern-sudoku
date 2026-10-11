// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.platform

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview

/**
 * play flavour (US-025, ADR-008): the Play In-App Review flow. It talks to the Play Store app on the device over IPC;
 * the app itself needs no INTERNET permission (the release APK's permission gate stays empty, NFR-004).
 */
class PlayReviewPrompter(private val manager: ReviewManager) : ReviewPrompter {
    override suspend fun launch(activity: Activity) {
        try {
            val info = manager.requestReview()
            manager.launchReview(activity, info)
        } catch (e: Exception) { // no Play Store, not from Play, or quota: the player just doesn't see a dialog
            Log.i("ReviewPrompter", "Review flow unavailable: ${e.javaClass.simpleName}")
        }
    }
}

object StoreReview {
    fun prompter(context: Context): ReviewPrompter = PlayReviewPrompter(ReviewManagerFactory.create(context))
}
