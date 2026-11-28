// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.platform

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * fdroid flavour (ADR-008): no payment library and no in-app payments. The Support screen offers GitHub Sponsors,
 * opened in the browser (ACTION_VIEW), so the app itself still never touches the network.
 */
object SponsorTipJar : TipJar {
    override val state: StateFlow<TipJarState> = MutableStateFlow(TipJarState.External(TipCatalog.SPONSOR_URL))
    override val outcomes: SharedFlow<TipOutcome> = MutableSharedFlow()

    override fun refresh() = Unit

    override fun buy(activity: Activity, tipId: String) = Unit
}

object StoreTips {
    @Suppress("UNUSED_PARAMETER")
    fun tipJar(context: Context): TipJar = SponsorTipJar
}
