package com.papercranelabs.gemblo.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play Billing for the one-time "No Ads" purchase.
 * Play Console: create an in-app product with id [NO_ADS] (one-time, $3.99) before release.
 * On start it also restores the purchase (reinstall / new phone), so [onOwned] can fire without a tap.
 * The debug build (".debug" app id) is not on Play, so the product never loads there; GameUi falls back to a free test toggle.
 */
class Store(context: Context, private val onOwned: () -> Unit) : PurchasesUpdatedListener {
    private val main = Handler(Looper.getMainLooper())
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()
    private var details: ProductDetails? = null

    /** Localized price from Play (e.g. "$3.99", "৳450.00"); null until loaded. */
    var price by mutableStateOf<String?>(null)
        private set

    fun start() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                if (r.responseCode != BillingClient.BillingResponseCode.OK) { Log.w(TAG, "setup: ${r.debugMessage}"); return }
                loadProduct()
                restore()
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(NO_ADS).setProductType(BillingClient.ProductType.INAPP).build(),
        )).build()
        client.queryProductDetailsAsync(params) { r, result ->
            val d = result.productDetailsList.firstOrNull()
            if (r.responseCode != BillingClient.BillingResponseCode.OK || d == null) { Log.w(TAG, "product: ${r.debugMessage}"); return@queryProductDetailsAsync }
            main.post { details = d; price = d.oneTimePurchaseOfferDetails?.formattedPrice }
        }
    }

    private fun restore() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { r, list ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) handle(list)
        }
    }

    /** Opens Google Play's purchase sheet. False when the store isn't ready (no connection, or not a Play build). */
    fun buy(activity: Activity): Boolean {
        val d = details ?: return false
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build(),
        )).build()
        return client.launchBillingFlow(activity, flow).responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(r: BillingResult, purchases: MutableList<Purchase>?) {
        if (r.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) handle(purchases)
        else if (r.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) restore()
    }

    private fun handle(purchases: List<Purchase>) {
        for (p in purchases) {
            if (NO_ADS !in p.products || p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            // Play refunds a purchase that is not acknowledged within 3 days
            if (!p.isAcknowledged) {
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { ar ->
                    if (ar.responseCode != BillingClient.BillingResponseCode.OK) Log.w(TAG, "acknowledge: ${ar.debugMessage}")
                }
            }
            main.post(onOwned)
        }
    }

    companion object {
        const val NO_ADS = "no_ads"
        private const val TAG = "GembloStore"
    }
}
