// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.game

/**
 * Play time (PRD §5, LLD §4.8): accumulates monotonic-clock deltas only while running, so wall-clock changes never move
 * it (TC-033). Runs while the Game screen is RESUMED and not paused; stops on the completing move.
 */
class PlayClock(private val now: () -> Long, initialMs: Long = 0) {
    private var base = initialMs
    private var startedAt: Long? = null

    val running: Boolean get() = startedAt != null

    fun start() {
        if (startedAt == null) startedAt = now()
    }

    fun stop() {
        startedAt?.let { base += now() - it }
        startedAt = null
    }

    fun elapsedMs(): Long = base + (startedAt?.let { now() - it } ?: 0)

    fun reset() {
        base = 0
        if (startedAt != null) startedAt = now()
    }
}
