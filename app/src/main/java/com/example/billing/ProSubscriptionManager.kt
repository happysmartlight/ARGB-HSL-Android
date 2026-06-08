package com.example.billing

import android.app.Activity
import android.content.Context
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
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProSubscriptionState(
    val isLoading: Boolean = true,
    val isBillingReady: Boolean = false,
    val isPro: Boolean = false,
    val productId: String = ProSubscriptionManager.PRO_PRODUCT_ID,
    val basePlanId: String = ProSubscriptionManager.PRO_ANNUAL_BASE_PLAN_ID,
    val priceText: String = ProSubscriptionManager.DEFAULT_PRICE_TEXT,
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    val canBuy: Boolean
        get() = isBillingReady && !isPro
}

class ProSubscriptionManager(context: Context) : PurchasesUpdatedListener {

    companion object {
        const val PRO_PRODUCT_ID = "argb_hsl_pro"
        const val PRO_ANNUAL_BASE_PLAN_ID = "annual-auto"
        const val DEFAULT_PRICE_TEXT = "$79.00/year"

        private const val PREFS = "argb_hsl_subscription"
        private const val KEY_IS_PRO = "is_pro"
        private const val KEY_LAST_TOKEN = "last_purchase_token"
        // DEBUG: cờ "dính" để Pro debug không bị Billing reset mỗi lần mở app.
        private const val KEY_DEBUG_PRO = "debug_pro_sticky"
    }

    private val debugProSticky: Boolean
        get() = BuildConfig.DEBUG && prefs.getBoolean(KEY_DEBUG_PRO, false)

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var productDetails: ProductDetails? = null
    private var isConnecting = false
    private val pendingConnectedActions = mutableListOf<() -> Unit>()

    private val _state = MutableStateFlow(
        ProSubscriptionState(
            isLoading = true,
            isPro = prefs.getBoolean(KEY_IS_PRO, false) || debugProSticky,
            statusMessage = "Đang kiểm tra gói Pro..."
        )
    )
    val state: StateFlow<ProSubscriptionState> = _state.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    init {
        connect {
            queryProductDetails()
            refreshPurchases()
        }
    }

    fun refresh() {
        connect {
            queryProductDetails()
            refreshPurchases()
        }
    }

    fun restorePurchases() {
        _state.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                statusMessage = "Đang khôi phục gói Pro..."
            )
        }
        connect {
            queryProductDetails()
            refreshPurchases(showRestoreMessage = true)
        }
    }

    fun launchPurchase(activity: Activity) {
        _state.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                statusMessage = "Đang mở Google Play..."
            )
        }
        connect {
            queryProductDetails {
                launchBillingFlow(activity)
            }
        }
    }

    fun setDebugEntitlement(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_IS_PRO, enabled)
            .putBoolean(KEY_DEBUG_PRO, enabled) // "dính": Billing sẽ không reset Pro debug
            .apply()
        _state.update {
            it.copy(
                isLoading = false,
                isPro = enabled,
                statusMessage = if (enabled) "Đã bật Pro tạm thời cho debug." else "Đã tắt Pro debug.",
                errorMessage = null
            )
        }
    }

    fun destroy() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                handlePurchases(purchases.orEmpty(), showSuccessMessage = true)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _state.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Bạn đã hủy thanh toán.",
                        errorMessage = null
                    )
                }
            }
            else -> {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = billingResult.debugMessage.ifBlank { "Không thể hoàn tất thanh toán." },
                        statusMessage = null
                    )
                }
            }
        }
    }

    private fun connect(onConnected: () -> Unit = {}) {
        if (billingClient.isReady) {
            _state.update { it.copy(isBillingReady = true) }
            onConnected()
            return
        }

        pendingConnectedActions += onConnected
        if (isConnecting) return

        isConnecting = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                isConnecting = false
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.update {
                        it.copy(
                            isBillingReady = true,
                            errorMessage = null,
                            statusMessage = it.statusMessage ?: "Google Play Billing sẵn sàng."
                        )
                    }
                    val actions = pendingConnectedActions.toList()
                    pendingConnectedActions.clear()
                    actions.forEach { it.invoke() }
                } else {
                    pendingConnectedActions.clear()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isBillingReady = false,
                            errorMessage = billingResult.debugMessage.ifBlank { "Không kết nối được Google Play Billing." },
                            statusMessage = null
                        )
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnecting = false
                _state.update {
                    it.copy(
                        isBillingReady = false,
                        statusMessage = "Google Play Billing bị ngắt, sẽ thử lại khi cần."
                    )
                }
            }
        })
    }

    private fun queryProductDetails(onReady: (() -> Unit)? = null) {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRO_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, result ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = billingResult.debugMessage.ifBlank { "Không lấy được thông tin gói Pro." }
                    )
                }
                return@queryProductDetailsAsync
            }

            val details = result.productDetailsList.firstOrNull { it.productId == PRO_PRODUCT_ID }
            productDetails = details

            if (details == null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Chưa tìm thấy subscription $PRO_PRODUCT_ID trên Google Play Console.",
                        statusMessage = null
                    )
                }
                return@queryProductDetailsAsync
            }

            _state.update {
                it.copy(
                    isLoading = false,
                    priceText = details.annualPriceText(),
                    errorMessage = null,
                    statusMessage = if (it.isPro) "Pro đang hoạt động." else null
                )
            }
            onReady?.invoke()
        }
    }

    private fun refreshPurchases(showRestoreMessage: Boolean = false) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases, showRestoreMessage = showRestoreMessage)
            } else {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = billingResult.debugMessage.ifBlank { "Không kiểm tra được trạng thái Pro." }
                    )
                }
            }
        }
    }

    private fun launchBillingFlow(activity: Activity) {
        val details = productDetails
        if (details == null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Gói Pro chưa sẵn sàng. Vui lòng thử lại sau."
                )
            }
            return
        }

        val offerToken = details.annualOfferToken()
        if (offerToken.isNullOrBlank()) {
            _state.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Base plan $PRO_ANNUAL_BASE_PLAN_ID chưa được kích hoạt trong Play Console."
                )
            }
            return
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.debugMessage.ifBlank { "Google Play không mở được màn thanh toán." },
                    statusMessage = null
                )
            }
        }
    }

    private fun handlePurchases(
        purchases: List<Purchase>,
        showSuccessMessage: Boolean = false,
        showRestoreMessage: Boolean = false
    ) {
        val proPurchase = purchases.firstOrNull { purchase ->
            PRO_PRODUCT_ID in purchase.products &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        val pendingPurchase = purchases.any { purchase ->
            PRO_PRODUCT_ID in purchase.products &&
                purchase.purchaseState == Purchase.PurchaseState.PENDING
        }

        when {
            proPurchase != null -> {
                prefs.edit()
                    .putBoolean(KEY_IS_PRO, true)
                    .putString(KEY_LAST_TOKEN, proPurchase.purchaseToken)
                    .apply()
                _state.update {
                    it.copy(
                        isLoading = false,
                        isPro = true,
                        errorMessage = null,
                        statusMessage = when {
                            showSuccessMessage -> "Cảm ơn bạn! Gói Pro đã được mở khóa."
                            showRestoreMessage -> "Đã khôi phục gói Pro."
                            else -> "Pro đang hoạt động."
                        }
                    )
                }
                acknowledgeIfNeeded(proPurchase)
            }
            pendingPurchase -> {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isPro = prefs.getBoolean(KEY_IS_PRO, false),
                        statusMessage = "Thanh toán đang chờ xử lý. Pro sẽ mở khi Google Play xác nhận.",
                        errorMessage = null
                    )
                }
            }
            debugProSticky -> {
                // DEBUG: giữ Pro debug, không để Billing reset.
                _state.update {
                    it.copy(
                        isLoading = false,
                        isPro = true,
                        statusMessage = "Pro debug (dính) đang bật.",
                        errorMessage = null
                    )
                }
            }
            else -> {
                prefs.edit()
                    .putBoolean(KEY_IS_PRO, false)
                    .remove(KEY_LAST_TOKEN)
                    .apply()
                _state.update {
                    it.copy(
                        isLoading = false,
                        isPro = false,
                        statusMessage = if (showRestoreMessage) "Không tìm thấy gói Pro đang hoạt động." else null,
                        errorMessage = null
                    )
                }
            }
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _state.update {
                    it.copy(
                        errorMessage = billingResult.debugMessage.ifBlank {
                            "Đã mở Pro, nhưng chưa acknowledge được giao dịch. Vui lòng mở lại app để thử lại."
                        }
                    )
                }
            }
        }
    }

    private fun ProductDetails.annualOffer(): ProductDetails.SubscriptionOfferDetails? {
        val offers = subscriptionOfferDetails.orEmpty()
        val forBasePlan = offers.filter { it.basePlanId == PRO_ANNUAL_BASE_PLAN_ID }
        // Google Play CHỈ trả về offer mà người dùng ĐỦ ĐIỀU KIỆN. Vì vậy nếu có
        // offer khuyến mãi (free trial / intro, offerId != null) thì ưu tiên dùng
        // nó để màn thanh toán hiện "dùng thử miễn phí"; người đã hết điều kiện sẽ
        // chỉ còn base plan giá thường.
        return forBasePlan.firstOrNull { it.offerId != null }
            ?: forBasePlan.firstOrNull()
            ?: offers.firstOrNull()
    }

    private fun ProductDetails.annualOfferToken(): String? =
        annualOffer()?.offerToken

    private fun ProductDetails.annualPriceText(): String {
        val price = annualOffer()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.lastOrNull()
            ?.formattedPrice

        return if (price.isNullOrBlank()) DEFAULT_PRICE_TEXT else "$price/year"
    }
}
