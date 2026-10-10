// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.feature.game.PlayClock
import org.junit.Test

/** PRD §5 play time: counts only while running; monotonic source, so wall-clock changes can't move it (TC-033). */
class PlayClockTest {
    private var now = 1_000L
    private val clock = PlayClock({ now }, initialMs = 5_000)

    @Test
    fun countsOnlyWhileRunning() {
        now += 10_000 // not started: background, pause screen
        assertThat(clock.elapsedMs()).isEqualTo(5_000)
        clock.start()
        now += 2_500
        assertThat(clock.elapsedMs()).isEqualTo(7_500)
        clock.stop()
        now += 60_000
        assertThat(clock.elapsedMs()).isEqualTo(7_500)
        clock.start()
        clock.start() // idempotent
        now += 500
        assertThat(clock.elapsedMs()).isEqualTo(8_000)
    }

    @Test
    fun resetRestartsFromZero_keepingTheRunState() {
        clock.start()
        now += 3_000
        clock.reset()
        assertThat(clock.elapsedMs()).isEqualTo(0)
        now += 1_000
        assertThat(clock.elapsedMs()).isEqualTo(1_000)
        assertThat(clock.running).isTrue()
    }
}
