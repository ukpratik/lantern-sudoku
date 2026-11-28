// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.support

import android.app.Activity
import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.platform.Tip
import io.github.ukpratik.lanternsudoku.platform.TipCatalog
import io.github.ukpratik.lanternsudoku.platform.TipJar
import io.github.ukpratik.lanternsudoku.platform.TipJarState
import io.github.ukpratik.lanternsudoku.platform.TipOutcome
import io.github.ukpratik.lanternsudoku.ui.theme.LanternTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings › Support Lantern with a fake tip jar: prices from the store, the no-unlock note, and the thank-you. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SupportScreenTest {
    @get:Rule val compose = createComposeRule()

    private class FakeJar(initial: TipJarState) : TipJar {
        override val state = MutableStateFlow(initial)
        override val outcomes = MutableSharedFlow<TipOutcome>(extraBufferCapacity = 1)
        val bought = mutableListOf<String>()
        var refreshes = 0
        override fun refresh() { refreshes++ }
        override fun buy(activity: Activity, tipId: String) { bought += tipId }
    }

    private val tips = listOf(Tip(TipCatalog.SMALL, "₹49.00"), Tip(TipCatalog.MEDIUM, "₹149.00"), Tip(TipCatalog.LARGE, "₹499.00"))

    @Test
    fun showsEveryTipWithItsStorePrice_andSaysNothingUnlocks() {
        val jar = FakeJar(TipJarState.Ready(tips))
        val vm = SupportViewModel(jar)
        compose.setContent { LanternTheme { SupportScreen(vm, onBack = {}) } }
        assertThat(jar.refreshes).isEqualTo(1)
        for (price in listOf("₹49.00", "₹149.00", "₹499.00")) compose.onNodeWithText(price).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("They don't unlock anything", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun thanksThePlayerWhenATipGoesThrough() {
        val jar = FakeJar(TipJarState.Ready(tips))
        val vm = SupportViewModel(jar)
        compose.setContent { LanternTheme { SupportScreen(vm, onBack = {}) } }
        jar.outcomes.tryEmit(TipOutcome.THANKS)
        compose.onNodeWithText("Thank you!", substring = true).assertIsDisplayed()
    }

    @Test
    fun unavailable_offersTryAgain() {
        val jar = FakeJar(TipJarState.Unavailable)
        val vm = SupportViewModel(jar)
        compose.setContent { LanternTheme { SupportScreen(vm, onBack = {}) } }
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        assertThat(jar.refreshes).isEqualTo(2)
    }

    @Test
    fun external_offersGitHubSponsors() {
        val jar = FakeJar(TipJarState.External(TipCatalog.SPONSOR_URL))
        val vm = SupportViewModel(jar)
        compose.setContent { LanternTheme { SupportScreen(vm, onBack = {}) } }
        compose.onNodeWithText("Open GitHub Sponsors").performScrollTo().assertIsDisplayed()
    }
}
