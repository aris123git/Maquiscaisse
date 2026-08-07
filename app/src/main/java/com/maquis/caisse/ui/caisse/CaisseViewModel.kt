package com.maquis.caisse.ui.caisse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.common.CartJson
import com.maquis.caisse.core.SettingsKeys
import com.maquis.caisse.data.print.EscPosPrinter
import com.maquis.caisse.domain.cart.CartOperations
import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.model.CartLine
import com.maquis.caisse.domain.model.CreateOrderRequest
import com.maquis.caisse.domain.model.DiningTable
import com.maquis.caisse.domain.model.Order
import com.maquis.caisse.domain.model.PaymentInput
import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.domain.model.Product
import com.maquis.caisse.domain.model.Sale
import com.maquis.caisse.domain.payment.PaymentCalculator
import com.maquis.caisse.domain.repository.OrderRepository
import com.maquis.caisse.domain.repository.SettingsRepository
import com.maquis.caisse.domain.repository.TableRepository
import com.maquis.caisse.domain.repository.UserRepository
import com.maquis.caisse.domain.usecase.ObserveActiveProductsUseCase
import com.maquis.caisse.domain.usecase.ResolveProductImageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** Overlay pavé numérique : ajout ou édition d'une ligne panier. */
data class QuantityOverlay(
    val productId: Long,
    val productName: String,
    val unitPrice: Long,
    val imagePath: String?,
    val quantityInput: String,
    val isEditing: Boolean,
)

data class PaymentFormState(
    val mode: PaymentMode = PaymentMode.CASH,
    val amountTendered: String = "",
    val cashAmount: String = "",
    val mobileMoneyAmount: String = "",
    val voucherAmount: String = "",
    val debtAmount: String = "",
    /** true si le commerçant a touché le champ « espèces tendues » en mixte. */
    val tenderedExplicit: Boolean = false,
    val activeField: PaymentField = PaymentField.TENDERED,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
) {
    val activeInput: String
        get() = when (activeField) {
            PaymentField.TENDERED -> amountTendered
            PaymentField.CASH -> cashAmount
            PaymentField.MOBILE_MONEY -> mobileMoneyAmount
            PaymentField.VOUCHER -> voucherAmount
            PaymentField.DEBT -> debtAmount
        }

    fun toPaymentInput(): PaymentInput = PaymentInput(
        mode = mode,
        amountTendered = when {
            mode == PaymentMode.CASH -> amountTendered.toLongOrNull()
            mode == PaymentMode.MIXED && tenderedExplicit -> amountTendered.toLongOrNull()
            else -> null
        },
        cashAmount = cashAmount.toLongOrNull() ?: 0L,
        mobileMoneyAmount = mobileMoneyAmount.toLongOrNull() ?: 0L,
        voucherAmount = voucherAmount.toLongOrNull() ?: 0L,
        debtAmount = debtAmount.toLongOrNull() ?: 0L,
    )
}

enum class PaymentField {
    TENDERED,
    CASH,
    MOBILE_MONEY,
    VOUCHER,
    DEBT,
}

data class CaisseUiState(
    val products: List<Product> = emptyList(),
    val searchQuery: String = "",
    val cart: List<CartLine> = emptyList(),
    val quantityOverlay: QuantityOverlay? = null,
    val showPayment: Boolean = false,
    val payment: PaymentFormState = PaymentFormState(),
    val completedSale: Sale? = null,
    val completedOrder: Order? = null,
    val snackbarMessage: String? = null,
    val waitresses: List<AppUser> = emptyList(),
    val tables: List<DiningTable> = emptyList(),
    val selectedWaitress: AppUser? = null,
    val selectedTable: DiningTable? = null,
    val tablesEnabled: Boolean = true,
) {
    val cartTotal: Long get() = CartOperations.total(cart)

    val filteredProducts: List<Product>
        get() {
            val q = searchQuery.trim()
            if (q.isEmpty()) return products
            return products.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.category.contains(q, ignoreCase = true)
            }
        }
}

@HiltViewModel
class CaisseViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeActiveProducts: ObserveActiveProductsUseCase,
    private val resolveImage: ResolveProductImageUseCase,
    private val orderRepository: OrderRepository,
    private val userRepository: UserRepository,
    private val tableRepository: TableRepository,
    private val settingsRepository: SettingsRepository,
    private val printer: EscPosPrinter,
) : ViewModel() {

    private val saleInFlight = AtomicBoolean(false)

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
        viewModelScope.launch {
            userRepository.observeWaitresses().collect { list ->
                _uiState.update { state ->
                    state.copy(
                        waitresses = list,
                        selectedWaitress = state.selectedWaitress
                            ?: list.firstOrNull(),
                    )
                }
            }
        }
        viewModelScope.launch {
            tableRepository.observeActive().collect { list ->
                _uiState.update { it.copy(tables = list) }
            }
        }
        viewModelScope.launch {
            settingsRepository.observe(SettingsKeys.TABLES_ENABLED).collect { value ->
                _uiState.update { it.copy(tablesEnabled = value != "false") }
            }
        }
    }

    fun imageFile(relativePath: String?): File? = resolveImage(relativePath)

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectWaitress(user: AppUser?) {
        _uiState.update { it.copy(selectedWaitress = user) }
    }

    fun selectTable(table: DiningTable?) {
        _uiState.update { it.copy(selectedTable = table) }
    }

    fun onProductTap(product: Product) {
        if (product.salePrice <= 0L) {
            _uiState.update {
                it.copy(snackbarMessage = "Prix de vente invalide pour ${product.name}")
            }
            return
        }
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
            if (overlay.isEditing) setCart(CartOperations.remove(_uiState.value.cart, overlay.productId))
            dismissQuantityOverlay()
            return
        }
        val line = CartLine(
            productId = overlay.productId,
            productName = overlay.productName,
            unitPrice = overlay.unitPrice,
            quantity = qty,
            imagePath = overlay.imagePath,
        )
        setCart(CartOperations.upsert(_uiState.value.cart, line, replace = overlay.isEditing))
        dismissQuantityOverlay()
    }

    fun deleteOverlayLine() {
        val overlay = _uiState.value.quantityOverlay ?: return
        if (overlay.isEditing) {
            setCart(CartOperations.remove(_uiState.value.cart, overlay.productId))
        }
        dismissQuantityOverlay()
    }

    private fun setCart(cart: List<CartLine>) {
        savedStateHandle[KEY_CART] = CartJson.encode(cart)
        _uiState.update { it.copy(cart = cart) }
    }

    /** Enregistre une commande maquis sans paiement obligatoire. */
    fun saveUnpaidOrder(onCreated: (Long) -> Unit = {}) {
        val state = _uiState.value
        if (state.cart.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "Panier vide") }
            return
        }
        if (!saleInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val order = orderRepository.createOrder(
                    CreateOrderRequest(
                        lines = state.cart,
                        waitressId = state.selectedWaitress?.id,
                        waitressName = state.selectedWaitress?.name,
                        tableId = if (state.tablesEnabled) state.selectedTable?.id else null,
                        tableLabel = if (state.tablesEnabled) state.selectedTable?.label else null,
                        markAsPaid = false,
                    ),
                )
                savedStateHandle[KEY_CART] = CartJson.encode(emptyList())
                _uiState.update {
                    it.copy(
                        cart = emptyList(),
                        completedOrder = order,
                        snackbarMessage = "Commande ${order.publicId} enregistrée — ouvre-la dans Commandes pour marquer payée",
                    )
                }
                val printNote = if (printer.isEnabled()) {
                    val r = printer.printOrder(order)
                    if (r.isFailure) " — ⚠ Impression : ${r.exceptionOrNull()?.message ?: "échec"}" else ""
                } else ""
                _uiState.update {
                    it.copy(
                        snackbarMessage = "Commande ${order.publicId} enregistrée — ouvre-la dans Commandes pour marquer payée$printNote",
                    )
                }
                onCreated(order.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = e.message ?: "Échec commande") }
            } finally {
                saleInFlight.set(false)
            }
        }
    }

    fun openPayment() {
        val state = _uiState.value
        if (state.cart.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "Panier vide") }
            return
        }
        if (state.cartTotal <= 0L) {
            _uiState.update { it.copy(snackbarMessage = "Montant total invalide") }
            return
        }
        _uiState.update {
            it.copy(
                showPayment = true,
                payment = PaymentFormState(
                    mode = PaymentMode.CASH,
                    amountTendered = it.cartTotal.toString(),
                    tenderedExplicit = true,
                    activeField = PaymentField.TENDERED,
                ),
            )
        }
    }

    fun dismissPayment() {
        if (_uiState.value.payment.isSaving) return
        _uiState.update {
            it.copy(showPayment = false, payment = PaymentFormState())
        }
    }

    fun selectPaymentMode(mode: PaymentMode) {
        val total = _uiState.value.cartTotal.toString()
        _uiState.update { state ->
            state.copy(
                payment = when (mode) {
                    PaymentMode.CASH -> PaymentFormState(
                        mode = mode,
                        amountTendered = total,
                        tenderedExplicit = true,
                        activeField = PaymentField.TENDERED,
                    )
                    PaymentMode.MIXED -> PaymentFormState(
                        mode = mode,
                        cashAmount = total,
                        amountTendered = "",
                        tenderedExplicit = false,
                        activeField = PaymentField.CASH,
                    )
                    else -> PaymentFormState(mode = mode)
                },
            )
        }
    }

    fun selectPaymentField(field: PaymentField) {
        _uiState.update { state ->
            state.copy(
                payment = state.payment.copy(
                    activeField = field,
                    errorMessage = null,
                    tenderedExplicit = state.payment.tenderedExplicit ||
                        field == PaymentField.TENDERED,
                ),
            )
        }
    }

    fun onPaymentDigitInput(value: String) {
        _uiState.update { state ->
            val p = state.payment
            val updated = when (p.activeField) {
                PaymentField.TENDERED -> p.copy(
                    amountTendered = value,
                    tenderedExplicit = true,
                    errorMessage = null,
                )
                PaymentField.CASH -> p.copy(cashAmount = value, errorMessage = null)
                PaymentField.MOBILE_MONEY -> p.copy(mobileMoneyAmount = value, errorMessage = null)
                PaymentField.VOUCHER -> p.copy(voucherAmount = value, errorMessage = null)
                PaymentField.DEBT -> p.copy(debtAmount = value, errorMessage = null)
            }
            state.copy(payment = updated)
        }
    }

    fun changePreview(): Long {
        val state = _uiState.value
        return PaymentCalculator.previewChange(state.cartTotal, state.payment.toPaymentInput())
    }

    fun canConfirmPayment(): Boolean {
        val state = _uiState.value
        if (state.payment.isSaving) return false
        return PaymentCalculator.validate(state.cartTotal, state.payment.toPaymentInput()).isSuccess
    }

    fun confirmPayment() {
        if (!saleInFlight.compareAndSet(false, true)) return
        val state = _uiState.value
        if (!state.showPayment) {
            saleInFlight.set(false)
            return
        }

        _uiState.update {
            it.copy(payment = it.payment.copy(isSaving = true, errorMessage = null))
        }

        viewModelScope.launch {
            try {
                val paymentInput = state.payment.toPaymentInput()
                val breakdown = PaymentCalculator.validate(state.cartTotal, paymentInput).getOrThrow()
                val order = orderRepository.createOrder(
                    CreateOrderRequest(
                        lines = state.cart,
                        waitressId = state.selectedWaitress?.id,
                        waitressName = state.selectedWaitress?.name,
                        tableId = if (state.tablesEnabled) state.selectedTable?.id else null,
                        tableLabel = if (state.tablesEnabled) state.selectedTable?.label else null,
                        markAsPaid = true,
                        paymentMode = breakdown.mode,
                        amountTendered = breakdown.amountTendered,
                        paymentAmount = breakdown.totalAmount,
                    ),
                )
                savedStateHandle[KEY_CART] = CartJson.encode(emptyList())
                val printNote = if (printer.isEnabled()) {
                    val r = printer.printOrder(order)
                    if (r.isFailure) " — ⚠ Impression : ${r.exceptionOrNull()?.message ?: "échec"}" else ""
                } else ""
                _uiState.update {
                    it.copy(
                        cart = emptyList(),
                        showPayment = false,
                        payment = PaymentFormState(),
                        completedOrder = order,
                        completedSale = null,
                        quantityOverlay = null,
                        snackbarMessage = "Commande ${order.publicId} payée$printNote",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        payment = it.payment.copy(
                            isSaving = false,
                            errorMessage = e.message ?: "Échec de la vente",
                        ),
                    )
                }
            } finally {
                saleInFlight.set(false)
            }
        }
    }

    fun dismissTicket() {
        _uiState.update { it.copy(completedSale = null, completedOrder = null) }
    }

    fun clearCart() {
        setCart(emptyList())
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    companion object {
        private const val KEY_CART = "caisse_cart_json"
    }
}
