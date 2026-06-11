package com.banglu.keyboard

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
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
import com.banglu.keyboard.account.BuildConfig

data class BillingEntitlementState(
    val connected: Boolean = false,
    val loading: Boolean = false,
    val proActive: Boolean = false,
    val message: String = "Play Billing প্রস্তুত হচ্ছে"
)

class BillingEntitlementManager(context: Context) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("banglu_prefs", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var listener: ((BillingEntitlementState) -> Unit)? = null
    private var lastState = BillingEntitlementState(
        proActive = prefs.getString("subscription_plan", "free") == "pro",
        message = if (prefs.getString("subscription_plan", "free") == "pro") "Pro entitlement cached" else "Free plan"
    )

    private val productId = BuildConfig.BILLING_SUBSCRIPTION_PRODUCT_ID

    init {
        BangluProcessGuards.requireUiProcess(appContext, "BillingEntitlementManager")
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun setListener(onState: (BillingEntitlementState) -> Unit) {
        listener = onState
        emit(lastState)
    }

    fun connect() {
        if (billingClient.isReady) {
            refreshEntitlement()
            return
        }
        emit(lastState.copy(loading = true, message = "Play Billing সংযোগ করা হচ্ছে"))
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    emit(lastState.copy(connected = true, loading = false, message = "Play Billing connected"))
                    refreshEntitlement()
                } else {
                    emit(
                        lastState.copy(
                            connected = false,
                            loading = false,
                            message = billingResult.debugMessage.ifBlank { "Play Billing unavailable" }
                        )
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                emit(lastState.copy(connected = false, loading = false, message = "Play Billing disconnected"))
            }
        })
    }

    fun refreshEntitlement() {
        if (!billingClient.isReady) {
            connect()
            return
        }
        emit(lastState.copy(loading = true, message = "Subscription যাচাই হচ্ছে"))
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                emit(
                    lastState.copy(
                        loading = false,
                        message = billingResult.debugMessage.ifBlank { "Subscription check failed" }
                    )
                )
                return@queryPurchasesAsync
            }
            processPurchases(purchases)
        }
    }

    fun loadProductDetails(onReady: (ProductDetails) -> Unit) {
        if (!billingClient.isReady) {
            connect()
            emit(lastState.copy(message = "আবার চেষ্টা করুন, Billing এখন connect হচ্ছে"))
            return
        }
        emit(lastState.copy(loading = true, message = "Pro plan লোড হচ্ছে"))
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, result ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                emit(
                    lastState.copy(
                        loading = false,
                        message = billingResult.debugMessage.ifBlank { "Pro plan পাওয়া যায়নি" }
                    )
                )
                return@queryProductDetailsAsync
            }
            val productDetails = result.productDetailsList.firstOrNull { it.productId == productId }
            if (productDetails == null) {
                emit(lastState.copy(loading = false, message = "Play Console-এ $productId product active নেই"))
            } else {
                emit(lastState.copy(loading = false, message = "Pro plan ready"))
                mainHandler.post { onReady(productDetails) }
            }
        }
    }

    fun launchPurchase(activity: Activity, productDetails: ProductDetails): BillingResult {
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken.isNullOrBlank()) {
            val result = BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.DEVELOPER_ERROR)
                .setDebugMessage("Subscription offer token missing")
                .build()
            emit(lastState.copy(message = result.debugMessage))
            return result
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .setObfuscatedAccountId(obfuscatedAccountId())
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            emit(lastState.copy(message = result.debugMessage.ifBlank { "Purchase flow failed" }))
        }
        return result
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED ->
                emit(lastState.copy(loading = false, message = "Purchase cancel করা হয়েছে"))
            else ->
                emit(lastState.copy(loading = false, message = billingResult.debugMessage.ifBlank { "Purchase failed" }))
        }
    }

    fun close() {
        listener = null
        if (billingClient.isReady) billingClient.endConnection()
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val activePurchase = purchases.firstOrNull { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.products.contains(productId)
        }
        if (activePurchase != null) {
            saveEntitlement(activePurchase)
            acknowledgeIfNeeded(activePurchase)
            emit(
                lastState.copy(
                    connected = billingClient.isReady,
                    loading = false,
                    proActive = true,
                    message = "Pro subscription active"
                )
            )
            return
        }

        prefs.edit()
            .putString("subscription_plan", "free")
            .putString("subscription_source", "play_billing")
            .remove("subscription_product_id")
            .remove("subscription_purchase_token")
            .putLong("subscription_checked_at", System.currentTimeMillis())
            .apply()
        emit(
            lastState.copy(
                connected = billingClient.isReady,
                loading = false,
                proActive = false,
                message = "Active Pro subscription পাওয়া যায়নি"
            )
        )
    }

    private fun saveEntitlement(purchase: Purchase) {
        prefs.edit()
            .putString("subscription_plan", "pro")
            .putString("subscription_source", "play_billing")
            .putString("subscription_product_id", productId)
            .putString("subscription_purchase_token", purchase.purchaseToken)
            .putLong("subscription_checked_at", System.currentTimeMillis())
            .apply()
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            val message = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                "Subscription acknowledged"
            } else {
                result.debugMessage.ifBlank { "Subscription acknowledgement failed" }
            }
            emit(lastState.copy(message = message))
        }
    }

    private fun obfuscatedAccountId(): String {
        val userId = prefs.getString("auth_user_id", null)?.takeIf { it.isNotBlank() }
        val email = prefs.getString("auth_email", null)?.takeIf { it.isNotBlank() }
        return (userId ?: email ?: "anonymous").hashCode().toString()
    }

    private fun emit(state: BillingEntitlementState) {
        lastState = state
        mainHandler.post { listener?.invoke(state) }
    }
}
