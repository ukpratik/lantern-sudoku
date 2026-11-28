// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.platform

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tip jar catalogue (both flavours): tips always show small to large, and only the ones Play actually returned. */
class TipCatalogTest {
    @Test
    fun ordersTipsSmallToLarge_whateverOrderPlayReturns() {
        val prices = mapOf(TipCatalog.LARGE to "₹499.00", TipCatalog.SMALL to "₹49.00", TipCatalog.MEDIUM to "₹149.00")
        assertThat(TipCatalog.ordered(prices)).containsExactly(
            Tip(TipCatalog.SMALL, "₹49.00"), Tip(TipCatalog.MEDIUM, "₹149.00"), Tip(TipCatalog.LARGE, "₹499.00"),
        ).inOrder()
    }

    @Test
    fun skipsMissingTips_andIgnoresUnknownProducts() {
        val prices = mapOf(TipCatalog.MEDIUM to "$1.99", "something_else" to "$9.99")
        assertThat(TipCatalog.ordered(prices)).containsExactly(Tip(TipCatalog.MEDIUM, "$1.99"))
        assertThat(TipCatalog.ordered(emptyMap())).isEmpty()
    }
}
