// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.data.ShareFacts
import io.github.ukpratik.lanternsudoku.engine.game.BoxFlags
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.feature.share.ShareText
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** TC-150: the exact share format (design completion-daily-share, §9), plurals, h:mm:ss, emoji precedence, no spoilers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ShareTextTest {
    private val thu = LocalDate.of(2026, 10, 15)
    private fun facts(elapsedMs: Long = 521_000, hints: Int = 0, mistakes: Int = 0, flags: Int = 0, level: Level = Level.MEDIUM, date: LocalDate = thu) =
        ShareFacts(date, level, elapsedMs, hints, mistakes, flags)

    @Test
    fun theDesignsExactText() {
        // One stage-2 Pointing hint in box 8 (reading order, 1-based) = index 7.
        val text = ShareText.build(facts(hints = 1, flags = BoxFlags.hint(7)))
        assertThat(text).isEqualTo("Lantern Sudoku · Daily 15 Oct 2026 · Medium · 8:41 · 1 hint · no mistakes\n🟨🟨🟨\n🟨🟨🟨\n🟨💡🟨")
    }

    @Test
    fun pluralsForHintsAndMistakes() {
        fun line(h: Int, m: Int) = ShareText.build(facts(hints = h, mistakes = m)).lines().first()
        assertThat(line(0, 0)).endsWith(" · no hints · no mistakes")
        assertThat(line(1, 1)).endsWith(" · 1 hint · 1 mistake")
        assertThat(line(2, 2)).endsWith(" · 2 hints · 2 mistakes")
        assertThat(line(12, 0)).endsWith(" · 12 hints · no mistakes")
    }

    @Test
    fun timesFromAnHourAreHMmSs() {
        assertThat(ShareText.build(facts(elapsedMs = 3_599_000)).lines().first()).contains(" · 59:59 · ")
        assertThat(ShareText.build(facts(elapsedMs = 3_600_000)).lines().first()).contains(" · 1:00:00 · ")
        assertThat(ShareText.build(facts(elapsedMs = 5_000)).lines().first()).contains(" · 0:05 · ")
    }

    @Test
    fun everyLevelAndASingleDigitDay() {
        val text = ShareText.build(facts(level = Level.EXPERT, date = LocalDate.of(2026, 11, 1)))
        assertThat(text.lines().first()).startsWith("Lantern Sudoku · Daily 1 Nov 2026 · Expert · ")
        assertThat(ShareText.build(facts(level = Level.EASY))).contains(" · Easy · ")
        assertThat(ShareText.build(facts(level = Level.HARD))).contains(" · Hard · ")
        // English month names whatever the device locale (the brand line isn't translated).
        assertThat(ShareText.build(facts(date = LocalDate.of(2027, 9, 5)))).contains("Daily 5 Sep 2027")
    }

    @Test
    fun emojiPrecedence_hintBeatsMistakeBeatsSolved_exactlyNine() {
        val flags = BoxFlags.hint(0) or BoxFlags.mistake(0) or // both → 💡
            BoxFlags.mistake(4) or // 🟧
            BoxFlags.hint(8)
        val grid = ShareText.build(facts(hints = 2, mistakes = 2, flags = flags)).lines().drop(1)
        assertThat(grid).containsExactly("💡🟨🟨", "🟨🟧🟨", "🟨🟨💡").inOrder()
        for (f in listOf(0, flags, (0 until 18).fold(0) { a, b -> a or (1 shl b) })) {
            val squares = ShareText.build(facts(flags = f)).lines().drop(1).joinToString("")
            val count = squares.codePoints().count()
            assertThat(count).isEqualTo(9)
        }
    }

    @Test
    fun spoilerFree_noGridDigits_noUrl() {
        val text = ShareText.build(facts(hints = 3, mistakes = 1, flags = BoxFlags.hint(3) or BoxFlags.mistake(5)))
        val grid = text.lines().drop(1)
        assertThat(grid).hasSize(3)
        assertThat(grid.joinToString("")).doesNotContainMatch("[0-9]")
        assertThat(text).doesNotContain("http")
        assertThat(text).doesNotContain("www")
        assertThat(text.lines()).hasSize(4)
    }

    @Test
    fun TC151_intentIsTextOnlyActionSend_inAChooserTitledShareYourResult() {
        val chooser = ShareText.intent(ApplicationProvider.getApplicationContext(), facts(hints = 1, flags = BoxFlags.hint(7)))
        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        assertThat(chooser.getCharSequenceExtra(Intent.EXTRA_TITLE).toString()).isEqualTo("Share your result")
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertThat(send.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(send.type).isEqualTo("text/plain")
        assertThat(send.getStringExtra(Intent.EXTRA_TEXT)).startsWith("Lantern Sudoku · Daily 15 Oct 2026")
        assertThat(send.hasExtra(Intent.EXTRA_STREAM)).isFalse()
    }
}
