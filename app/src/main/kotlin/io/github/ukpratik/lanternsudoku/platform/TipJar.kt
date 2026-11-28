// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.platform

import android.app.Activity
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** One tip the player can give, with the store's localised price ("₹49.00", "$0.99"). */
data class Tip(val id: String, val price: String)

/** What the Support screen can offer right now. */
sealed interface TipJarState {
    data object Loading : TipJarState

    /** play: the tips Google Play returned, in catalogue order. */
    data class Ready(val tips: List<Tip>) : TipJarState

    /** play: no Play Store, billing not available, or no tips set up in Play Console. */
    data object Unavailable : TipJarState

    /** fdroid: no in-app payments (ADR-008); support happens on a web page opened in the browser. */
    data class External(val url: String) : TipJarState
}

/** How a tip ended. Nothing is unlocked either way: every feature stays free for everyone. */
enum class TipOutcome { THANKS, PENDING, CANCELLED, FAILED }

/**
 * The tip jar (Settings › Support Lantern). play: Google Play Billing, consumable one-time products, so the same tip can
 * be given again. fdroid: a link to GitHub Sponsors, no payment library at all (ADR-008).
 */
interface TipJar {
    val state: StateFlow<TipJarState>

    /** One event per finished purchase flow. */
    val outcomes: SharedFlow<TipOutcome>

    /** Connects if needed, loads the tips, and finishes any tip left over from an earlier session. */
    fun refresh()

    fun buy(activity: Activity, tipId: String)
}

/** The tips set up in Play Console (one-time, consumable products). Pure, so it's unit-tested on both flavours. */
object TipCatalog {
    const val SMALL = "tip_small"
    const val MEDIUM = "tip_medium"
    const val LARGE = "tip_large"
    val IDS = listOf(SMALL, MEDIUM, LARGE)

    const val SPONSOR_URL = "https://github.com/sponsors/ukpratik"

    /** The tips Play found, in catalogue order (small to large) whatever order Play returned them in. */
    fun ordered(prices: Map<String, String>): List<Tip> = IDS.mapNotNull { id -> prices[id]?.let { Tip(id, it) } }
}
