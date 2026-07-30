package com.maquis.caisse.ui.caisse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.common.CartJson
import com.maquis.caisse.domain.model.CartLine
import com.maquis.caisse.domain.model.CompleteSaleRequest
import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.domain.model.Product
import com.maquis.caisse.domain.model.Sale
import com.maquis.caisse.domain.usecase.CompleteSaleUseCase
import com.maquis.caisse.domain.usecase.ObserveActiveProductsUseCase
import com.maquis.caisse.domain.usecase.ResolveProductImageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Overlay pavé numérique : ajout ou édition d'une ligne panier. */
data class QuantityOverlay(
    val productId: Long,
    val productName: String,
    val unitPrice: Long,
    val imagePath: String?,
    val quantityInput: String,
    /** true si on édite une ligne déjà au panier (long-press). */
    val isEditing: Boolean,
)

data class PaymentFormState(
    val mode: PaymentMode = PaymentMode.CASH,
    val amountTendered: String = "",
    val cashAmount: String = "",
    val mobileMoneyAmount: String = "",
    val voucherAmount: String = "",
    val debtAmount: String = "",
    /** Quel champ mixte / espèces reçoit le pavé. */
    val activeField: PaymentField = PaymentField.TENDERED,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
)

enum class PaymentField {
    TENDERED,
    CASH,
    MOBILE_MONEY,
    VOUCHER,
    DEBT,
}

data class CaisseUiState(
    val products: List<Product> = emptyList(),
    val cart: List<CartLine> = emptyList(),
    val quantityOverlay: QuantityOverlay? = null,
    val showPayment: Boolean = false,
    val payment: PaymentFormState = PaymentFormState(),
    val completedSale: Sale? = null,
    val snackbarMessage: String? = null,
) {
    val cartTotal: Long get() = cart.sumOf { it.lineTotal }
    val cartItemCount: Int get() = cart.sumOf { it.quantity }
}

@HiltViewModel
class CaisseViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeActiveProducts: ObserveActiveProductsUseCase,
    private val completeSale: CompleteSaleUseCase,
    private val resolveImage: ResolveProductImageUseCase,
) : ViewModel() {

    private val productsFlow = observeActiveProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(
        CaisseUiState(cart = CartJson.decode(savedStateHandle[KEY_CART])),
    )
    val uiState: StateFlow<CaisseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            productsFlow.collect { products ->
                _uiState.update { it.copy(products = products) }
            }
        }
    }

    fun imageFile(relativePath: String?): File? = resolveImage(relativePath)

    fun onProductTap(product: Product) {
        _uiState.update {
            it.copy(
                quantityOverlay = QuantityOverlay(
                    productId = product.id,
                    productName = product.name,
                    unitPrice = product.salePrice,
                    imagePath = product.imagePath,
                    quantityInput = "1",
                    isEditing = false,
                ),
            )
        }
    }

    fun onCartLineLongPress(line: CartLine) {
        _uiState.update {
            it.copy(
                quantityOverlay = QuantityOverlay(
                    productId = line.productId,
                    productName = line.productName,
                    unitPrice = line.unitPrice,
                    imagePath = line.imagePath,
                    quantityInput = line.quantity.toString(),
                    isEditing = true,
                ),
            )
        }
    }

    fun onQuantityInputChange(value: String) {
        _uiState.update { state ->
            val overlay = state.quantityOverlay ?: return@update state
            state.copy(quantityOverlay = overlay.copy(quantityInput = value))
        }
    }

    fun dismissQuantityOverlay() {
        _uiState.update { it.copy(quantityOverlay = null) }
    }

    fun confirmQuantity() {
        val overlay = _uiState.value.quantityOverlay ?: return
        val qty = overlay.quantityInput.toIntOrNull() ?: return
        if (qty <= 0) {
            if (overlay.isEditing) {
                removeFromCart(overlay.productId)
            }
            dismissQuantityOverlay()
            return
        }
        upsertCartLine(
            CartLine(
                productId = overlay.productId,
                productName = overlay.productName,
                unitPrice = overlay.unitPrice,
                quantity = qty,
                imagePath = overlay.imagePath,
            ),
            replace = overlay.isEditing,
        )
        dismissQuantityOverlay()
    }

    fun deleteOverlayLine() {
        val overlay = _uiState.value.quantityOverlay ?: return
        if (overlay.isEditing) {
            removeFromCart(overlay.productId)
        }
        dismissQuantityOverlay()
    }

    /**
     * @param replace si true (édition), remplace la quantité ; sinon additionne (doublons).
     */
    private fun upsertCartLine(line: CartLine, replace: Boolean) {
        _uiState.update { state ->
            val existing = state.cart.find { it.productId == line.productId }
            val newCart = when {
                existing == null -> state.cart + line
                replace -> state.cart.map {
                    if (it.productId == line.productId) line else it
                }
                else -> state.cart.map {
                    if (it.productId == line.productId) {
                        it.copy(quantity = it.quantity + line.quantity)
                    } else {
                        it
                    }
                }
            }
            persistCart(newCart)
            state.copy(cart = newCart)
        }
    }

    private fun removeFromCart(productId: Long) {
        _uiState.update { state ->
            val newCart = state.cart.filterNot { it.productId == productId }
            persistCart(newCart)
            state.copy(cart = newCart)
        }
    }

    private fun persistCart(cart: List<CartLine>) {
        savedStateHandle[KEY_CART] = CartJson.encode(cart)
    }

    fun openPayment() {
        val state = _uiState.value
        if (state.cart.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "Panier vide") }
            return
        }
        _uiState.update {
            it.copy(
                showPayment = true,
                payment = PaymentFormState(
                    mode = PaymentMode.CASH,
                    amountTendered = it.cartTotal.toString(),
                    activeField = PaymentField.TENDERED,
                ),
            )
        }
    }

    fun dismissPayment() {
        _uiState.update {
            it.copy(showPayment = false, payment = PaymentFormState())
        }
    }

    fun selectPaymentMode(mode: PaymentMode) {
        _uiState.update { state ->
            val total = state.cartTotal.toString()
            state.copy(
                payment = PaymentFormState(
                    mode = mode,
                    amountTendered = if (mode == PaymentMode.CASH || mode == PaymentMode.MIXED) {
                        total
                    } else {
                        ""
                    },
                    cashAmount = if (mode == PaymentMode.MIXED) total else "",
                    activeField = when (mode) {
                        PaymentMode.CASH -> PaymentField.TENDERED
                        PaymentMode.MIXED -> PaymentField.CASH
                        else -> PaymentField.TENDERED
                    },
                ),
            )
        }
    }

    fun selectPaymentField(field: PaymentField) {
        _uiState.update { state ->
            state.copy(payment = state.payment.copy(activeField = field, errorMessage = null))
        }
    }

    fun onPaymentDigitInput(value: String) {
        _uiState.update { state ->
            val p = state.payment
            val updated = when (p.activeField) {
                PaymentField.TENDERED -> p.copy(amountTendered = value, errorMessage = null)
                PaymentField.CASH -> p.copy(cashAmount = value, errorMessage = null)
                PaymentField.MOBILE_MONEY -> p.copy(mobileMoneyAmount = value, errorMessage = null)
                PaymentField.VOUCHER -> p.copy(voucherAmount = value, errorMessage = null)
                PaymentField.DEBT -> p.copy(debtAmount = value, errorMessage = null)
            }
            state.copy(payment = updated)
        }
    }

    fun currentPaymentInput(): String {
        val p = _uiState.value.payment
        return when (p.activeField) {
            PaymentField.TENDERED -> p.amountTendered
            PaymentField.CASH -> p.cashAmount
            PaymentField.MOBILE_MONEY -> p.mobileMoneyAmount
            PaymentField.VOUCHER -> p.voucherAmount
            PaymentField.DEBT -> p.debtAmount
        }
    }

    fun confirmPayment() {
        val state = _uiState.value
        val payment = state.payment
        if (payment.isSaving) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(payment = it.payment.copy(isSaving = true, errorMessage = null))
            }
            try {
                val request = CompleteSaleRequest(
                    lines = state.cart,
                    paymentMode = payment.mode,
                    cashAmount = payment.cashAmount.toLongOrNull() ?: 0L,
                    mobileMoneyAmount = payment.mobileMoneyAmount.toLongOrNull() ?: 0L,
                    voucherAmount = payment.voucherAmount.toLongOrNull() ?: 0L,
                    debtAmount = payment.debtAmount.toLongOrNull() ?: 0L,
                    amountTendered = payment.amountTendered.toLongOrNull() ?: 0L,
                )
                val sale = completeSale(request)
                persistCart(emptyList())
                _uiState.update {
                    it.copy(
                        cart = emptyList(),
                        showPayment = false,
                        payment = PaymentFormState(),
                        completedSale = sale,
                        quantityOverlay = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        payment = it.payment.copy(
                            isSaving = false,
                            errorMessage = e.message ?: "Échec de la vente",
                        ),
                    )
                }
            }
        }
    }

    fun dismissTicket() {
        _uiState.update { it.copy(completedSale = null) }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    companion object {
        private const val KEY_CART = "caisse_cart_json"
    }
}
