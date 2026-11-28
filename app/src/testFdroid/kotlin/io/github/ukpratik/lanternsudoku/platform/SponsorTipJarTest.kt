// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.platform

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** fdroid flavour (ADR-008): the tip jar is a GitHub Sponsors link, and no Play Billing class ships. */
class SponsorTipJarTest {
    @Test
    fun fdroidLinksToSponsors_andShipsNoBillingLibrary() {
        val jar = StoreTips.tipJar(android.content.ContextWrapper(null))
        assertThat(jar.state.value).isEqualTo(TipJarState.External(TipCatalog.SPONSOR_URL))
        assertThat(runCatching { Class.forName("com.android.billingclient.api.BillingClient") }.isFailure).isTrue()
    }
}
