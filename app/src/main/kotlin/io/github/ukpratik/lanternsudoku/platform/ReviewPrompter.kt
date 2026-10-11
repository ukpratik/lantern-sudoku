// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.platform

import android.app.Activity

/**
 * The store's rating dialog (US-025, LLD §4.12). play: Google Play In-App Review; fdroid: nothing (no proprietary
 * library, ADR-008). Whether to ask at all is [RatePolicy]'s call, made before this is reached.
 */
fun interface ReviewPrompter {
    /** Requests the review flow. Play decides whether it shows (quota); nothing is reported back. */
    suspend fun launch(activity: Activity)

    companion object {
        val NONE = ReviewPrompter { }
    }
}

/**
 * US-025 rules (PRD §6, TC-190): only right after a completion (the caller makes sure of that), at least 5 completed
 * puzzles, never on the day of the first launch, at most once per 60 days. Pure, so it's unit-tested on both flavours.
 */
object RatePolicy {
    const val MIN_COMPLETED = 5
    const val QUIET_DAYS = 60L
    private const val DAY_MS = 86_400_000L

    fun shouldAsk(completed: Int, todayEpochDay: Long, firstLaunchEpochDay: Long?, lastPromptAtMs: Long, nowMs: Long): Boolean =
        completed >= MIN_COMPLETED &&
            firstLaunchEpochDay != null && todayEpochDay > firstLaunchEpochDay &&
            (lastPromptAtMs <= 0L || nowMs - lastPromptAtMs >= QUIET_DAYS * DAY_MS)
}

/**
 * Asks for a rating when [RatePolicy] allows (US-025), recording the request first so a quota-hidden dialog still
 * counts toward the 60 days (LLD §4.12). Called once the player leaves the summary of a puzzle they just completed
 * (TC-192): never mid-game, never on the summary itself.
 */
class RatePrompt(
    private val settings: io.github.ukpratik.lanternsudoku.data.SettingsRepository,
    private val completedCount: suspend () -> Int,
    private val todayEpochDay: () -> Long,
    private val prompter: ReviewPrompter,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** True when the review flow was requested. */
    suspend fun maybeAsk(activity: Activity): Boolean {
        val marks = settings.marks()
        val at = now()
        if (!RatePolicy.shouldAsk(completedCount(), todayEpochDay(), marks.firstLaunchEpochDay, marks.lastReviewPromptAt, at)) return false
        settings.recordReviewPrompt(at)
        prompter.launch(activity)
        return true
    }
}
