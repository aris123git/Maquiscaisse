package com.maquis.caisse.ui.commandes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.data.print.EscPosPrinter
import com.maquis.caisse.domain.model.Order
import com.maquis.caisse.domain.model.OrderLine
import com.maquis.caisse.domain.model.OrderStatus
import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.domain.model.Permissions
import com.maquis.caisse.domain.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrderDetailUiState(
    val order: Order? = null,
    val editing: Boolean = false,
    val editLines: List<OrderLine> = emptyList(),
    val message: String? = null,
    val error: String? = null,
    val isBusy: Boolean = false,
    /** Annuler / modifier : administrateur uniquement. */
    val canModifyOrCancel: Boolean = false,
    /** Marquer payé : admin ou caissier selon permissions. */
    val canMarkPaid: Boolean = false,
    /** Après paiement total : retourner à Commandes en cours. */
    val navigateToOpenOrders: Boolean = false,
)

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val printer: EscPosPrinter,
    private val session: SessionManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(OrderDetailUiState())
    val ui: StateFlow<OrderDetailUiState> = _ui.asStateFlow()

    private fun isAdmin(): Boolean =
        session.userOrNull()?.role == "ADMIN"

    private fun permissionFlags() = Pair(
        first = isAdmin(),
        second = session.can(Permissions.MARK_PAID),
    )

    fun load(orderId: Long) {
        viewModelScope.launch {
            _ui.update { it.copy(isBusy = true, error = null) }
            try {
                val order = orderRepository.getOrder(orderId)
                val (canMod, canPay) = permissionFlags()
                _ui.update {
                    it.copy(
                        order = order,
                        editLines = order?.items.orEmpty(),
                        isBusy = false,
                        canModifyOrCancel = canMod,
                        canMarkPaid = canPay,
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(isBusy = false, error = e.message) }
            }
        }
    }

    fun startEdit() {
        if (!isAdmin()) {
            _ui.update { it.copy(error = "Seul l'administrateur peut modifier une commande") }
            return
        }
        val order = _ui.value.order ?: return
        if (!order.isOpen) return
        _ui.update { it.copy(editing = true, editLines = order.items) }
    }

    fun cancelEdit() {
        _ui.update { it.copy(editing = false, editLines = _ui.value.order?.items.orEmpty()) }
    }

    fun setQuantity(productId: Long, quantity: Int) {
        _ui.update { state ->
            val lines = state.editLines.mapNotNull { line ->
                if (line.productId != productId) {
                    line
                } else if (quantity <= 0) {
                    null
                } else {
                    line.copy(quantity = quantity)
                }
            }
            state.copy(editLines = lines)
        }
    }

    fun removeLine(productId: Long) {
        if (!isAdmin()) return
        _ui.update { state ->
            state.copy(editLines = state.editLines.filter { it.productId != productId })
        }
    }

    fun saveEdits() {
        if (!isAdmin()) {
            _ui.update { it.copy(error = "Seul l'administrateur peut modifier une commande") }
            return
        }
        val orderId = _ui.value.order?.id ?: return
        viewModelScope.launch {
            _ui.update { it.copy(isBusy = true, error = null) }
            try {
                val updated = orderRepository.updateOrderItems(orderId, _ui.value.editLines)
                _ui.update {
                    it.copy(
                        order = updated,
                        editLines = updated.items,
                        editing = false,
                        isBusy = false,
                        message = "Commande mise à jour",
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(isBusy = false, error = e.message) }
            }
        }
    }

    fun cancelOrder() {
        if (!isAdmin()) {
            _ui.update { it.copy(error = "Seul l'administrateur peut annuler une commande") }
            return
        }
        val orderId = _ui.value.order?.id ?: return
        viewModelScope.launch {
            _ui.update { it.copy(isBusy = true, error = null) }
            try {
                val updated = orderRepository.cancelOrder(orderId)
                _ui.update {
                    it.copy(order = updated, isBusy = false, message = "Commande annulée")
                }
            } catch (e: Exception) {
                _ui.update { it.copy(isBusy = false, error = e.message) }
            }
        }
    }

    /**
     * @param amountReceived montant reçu (pour espèces / partiel)
     * @param payFull si true, encaisse le reste (avec monnaie si reçu > reste)
     */
    fun markPaid(mode: PaymentMode, amountReceived: Long, payFull: Boolean) {
        if (!session.can(Permissions.MARK_PAID)) {
            _ui.update { it.copy(error = "Permission insuffisante") }
            return
        }
        val order = _ui.value.order ?: return
        val remaining = order.remainingAmount
        if (remaining <= 0L) return

        val payAmount: Long
        val tendered: Long
        if (payFull) {
            payAmount = remaining
            tendered = if (mode == PaymentMode.CASH) {
                amountReceived.coerceAtLeast(remaining)
            } else {
                remaining
            }
        } else {
            payAmount = amountReceived.coerceAtMost(remaining).coerceAtLeast(0L)
            tendered = if (mode == PaymentMode.CASH) amountReceived else payAmount
        }
        if (payAmount <= 0L) {
            _ui.update { it.copy(error = "Montant invalide") }
            return
        }

        viewModelScope.launch {
            _ui.update { it.copy(isBusy = true, error = null) }
            try {
                val updated = orderRepository.payOrder(
                    orderId = order.id,
                    mode = mode,
                    amount = payAmount,
                    amountTendered = tendered,
                )
                if (printer.isEnabled()) {
                    val printResult = printer.printOrder(updated)
                    val fullyPaid = updated.status == OrderStatus.PAYEE
                    val payMsg = if (fullyPaid) "Commande payée" else "Paiement partiel enregistré"
                    val printHint = if (printResult.isFailure) {
                        " — Impression : ${printResult.exceptionOrNull()?.message ?: "échec"}"
                    } else {
                        ""
                    }
                    _ui.update {
                        it.copy(
                            order = updated,
                            isBusy = false,
                            message = payMsg + printHint,
                            error = if (printResult.isFailure) {
                                printResult.exceptionOrNull()?.message
                            } else {
                                null
                            },
                            navigateToOpenOrders = fullyPaid,
                        )
                    }
                } else {
                    val fullyPaid = updated.status == OrderStatus.PAYEE
                    _ui.update {
                        it.copy(
                            order = updated,
                            isBusy = false,
                            message = if (fullyPaid) "Commande payée" else "Paiement partiel enregistré",
                            navigateToOpenOrders = fullyPaid,
                        )
                    }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(isBusy = false, error = e.message) }
            }
        }
    }

    fun consumeNavigateToOpenOrders() {
        _ui.update { it.copy(navigateToOpenOrders = false) }
    }

    fun printTicket() {
        val order = _ui.value.order ?: return
        viewModelScope.launch {
            if (!printer.isEnabled()) {
                _ui.update { it.copy(message = "Impression désactivée") }
                return@launch
            }
            val result = printer.printOrder(order)
            _ui.update {
                it.copy(
                    message = if (result.isSuccess) "Ticket imprimé" else result.exceptionOrNull()?.message,
                    error = if (result.isFailure) result.exceptionOrNull()?.message else null,
                )
            }
        }
    }

    fun consumeMessage() {
        _ui.update { it.copy(message = null, error = null) }
    }
}
