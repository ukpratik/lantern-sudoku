// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.platform

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * play flavour: tips through Google Play Billing. It talks to the Play Store app over IPC, so the app still has no
 * INTERNET permission; the only permission it adds is com.android.vending.BILLING (scripts/check-permissions.sh).
 * Tips are consumable one-time products: each purchase is consumed straight away, unlocks nothing, and can be repeated.
 * There's no server, so there's nothing to verify: a tip that isn't consumed just gets consumed on the next [refresh].
 */
class PlayTipJar(context: Context) : TipJar, PurchasesUpdatedListener {
    private val _state = MutableStateFlow<TipJarState>(TipJarState.Loading)
    override val state: StateFlow<TipJarState> = _state.asStateFlow()

    private val _outcomes = MutableSharedFlow<TipOutcome>(extraBufferCapacity = 4)
    override val outcomes: SharedFlow<TipOutcome> = _outcomes.asSharedFlow()

    private val details = ConcurrentHashMap<String, ProductDetails>()

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override fun refresh() {
        if (client.isReady) {
            load()
            return
        }
        if (_state.value !is TipJarState.Ready) _state.value = TipJarState.Loading
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) load() else unavailable("setup", result)
            }

            override fun onBillingServiceDisconnected() = Unit // enableAutoServiceReconnection() reconnects on the next call
        })
    }

    private fun load() {
        val products = TipCatalog.IDS.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.INAPP).build()
        }
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build()) { result, found ->
            if (result.responseCode != BillingResponseCode.OK) return@queryProductDetailsAsync unavailable("products", result)
            found.productDetailsList.forEach { details[it.productId] = it }
            val prices = details.values.mapNotNull { d -> offer(d)?.formattedPrice?.let { d.productId to it } }.toMap()
            val tips = TipCatalog.ordered(prices)
            _state.value = if (tips.isEmpty()) TipJarState.Unavailable else TipJarState.Ready(tips)
        }
        // A tip that was pending last time (and has since gone through) or never got consumed: finish it quietly.
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) purchases.forEach { handle(it, announce = false) }
        }
    }

    override fun buy(activity: Activity, tipId: String) {
        val product = details[tipId] ?: return emit(TipOutcome.FAILED)
        val params = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .apply { offer(product)?.offerToken?.let(::setOfferToken) }
            .build()
        val result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build())
        if (result.responseCode != BillingResponseCode.OK) {
            Log.i(TAG, "Billing flow not started: ${result.responseCode}")
            emit(TipOutcome.FAILED)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> purchases.orEmpty().forEach { handle(it, announce = true) }
            BillingResponseCode.USER_CANCELED -> emit(TipOutcome.CANCELLED)
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // An earlier tip of this size was never consumed: consume it now so the player can tip again.
                load()
                emit(TipOutcome.FAILED)
            }
            else -> emit(TipOutcome.FAILED)
        }
    }

    private fun handle(purchase: Purchase, announce: Boolean) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { result, _ ->
                    // Not consumed yet: it's retried on the next refresh. The player has paid either way, so say thanks.
                    if (result.responseCode != BillingResponseCode.OK) Log.i(TAG, "Consume failed: ${result.responseCode}")
                }
                if (announce) emit(TipOutcome.THANKS)
            }
            Purchase.PurchaseState.PENDING -> if (announce) emit(TipOutcome.PENDING)
            else -> Unit
        }
    }

    private fun offer(product: ProductDetails): ProductDetails.OneTimePurchaseOfferDetails? =
        product.oneTimePurchaseOfferDetailsList?.firstOrNull() ?: product.oneTimePurchaseOfferDetails

    private fun unavailable(step: String, result: BillingResult) {
        Log.i(TAG, "Tips unavailable at $step: ${result.responseCode}")
        if (_state.value !is TipJarState.Ready) _state.value = TipJarState.Unavailable
    }

    private fun emit(outcome: TipOutcome) {
        _outcomes.tryEmit(outcome)
    }

    private companion object {
        const val TAG = "TipJar"
    }
}

object StoreTips {
    fun tipJar(context: Context): TipJar = PlayTipJar(context)
}
