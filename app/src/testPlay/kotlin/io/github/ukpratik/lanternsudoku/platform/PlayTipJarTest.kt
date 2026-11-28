// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.platform

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** play flavour: the tip jar is Play Billing; without a Play Store it never crashes and never shows tips. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayTipJarTest {
    @Test
    fun playUsesBilling_andIsNeverReadyWithoutAPlayStore() {
        val jar = StoreTips.tipJar(ApplicationProvider.getApplicationContext())
        assertThat(jar).isInstanceOf(PlayTipJar::class.java)
        jar.refresh()
        assertThat(jar.state.value).isNotInstanceOf(TipJarState.Ready::class.java)
    }
}
