// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature

import android.app.Application
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.ui.components.sheetTitle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISS-005 (TC-009, TC-114): "a"/"an" never sits in front of a placeholder, whose value can start with a vowel sound
 * ("a Easy", "a 8"), and never in front of a written vowel ("a Expert"). Covers every string resource in the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CopyArticlesTest {
    private val res: Resources = ApplicationProvider.getApplicationContext<Application>().resources

    private val allStrings: Map<String, String> = R.string::class.java.fields.associate { it.name to res.getString(it.getInt(null)) }

    @Test
    fun noArticleBeforeAPlaceholderOrAVowel() {
        assertThat(allStrings.size).isGreaterThan(100)
        // "a useful", "a one-off": u/o words that start with a consonant sound are fine, so only a/e/i and digits 8 count.
        val bad = Regex("""\b[Aa]n? %|\b[Aa] [AEIaei8]|\b[Aa]n [^aeiouAEIOU%]""")
        val problems = allStrings.filter { (_, text) -> bad.containsMatchIn(text) }
        assertWithMessage("strings with a wrong or risky article").that(problems).isEmpty()
    }

    @Test
    fun continueOrNewTitle_usesTheRightArticlePerLevel() {
        assertThat(Level.entries.map { res.getString(it.sheetTitle) }).containsExactly(
            "You have an Easy game going",
            "You have a Medium game going",
            "You have a Hard game going",
            "You have an Expert game going",
        ).inOrder()
    }

    @Test
    fun hiddenSingleExplanation_namesTheDigitWithoutAnArticle() {
        val text = res.getString(R.string.tech_expl_hidden_single, "box 2", 8)
        assertThat(text).contains("already sees the digit 8 in its row")
    }
}
