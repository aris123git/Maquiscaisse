package com.maquis.caisse.ui.suivi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.model.Expense
import com.maquis.caisse.domain.model.ExpenseCategories
import com.maquis.caisse.domain.model.Permissions
import com.maquis.caisse.domain.model.StatsPeriod
import com.maquis.caisse.domain.model.StockMovement
import com.maquis.caisse.domain.repository.ExpenseRepository
import com.maquis.caisse.domain.repository.StockRepository
import com.maquis.caisse.domain.repository.UserRepository
import com.maquis.caisse.ui.common.DateRanges
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SuiviAdminUiState(
    val canAccess: Boolean = false,
    val canManageExpenses: Boolean = false,
    val tab: SuiviTab = SuiviTab.STOCK,
    val period: StatsPeriod = StatsPeriod.TODAY,
    val customDayMs: Long = System.currentTimeMillis(),
    val customFromMs: Long = DateRanges.todayBounds().first,
    val customToMs: Long = DateRanges.todayBounds().second,
    val filterUserId: Long? = null,
    val filterMovementType: String? = null,
    val movements: List<StockMovement> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val expensesTotal: Long = 0L,
    val loading: Boolean = false,
    val error: String? = null,
    val success: String? = null,
    val showAddExpense: Boolean = false,
    val expenseDescription: String = "",
    val expenseAmountText: String = "",
    val expenseCategory: String = ExpenseCategories.last(),
)

enum class SuiviTab(val label: String) {
    STOCK("Mouvements stock"),
    EXPENSES("Dépenses"),
}

@HiltViewModel
class SuiviAdminViewModel @Inject constructor(
    private val stockRepository: StockRepository,
    private val expenseRepository: ExpenseRepository,
    userRepository: UserRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        SuiviAdminUiState(
            canAccess = canAccessReports(),
            canManageExpenses = canManageExpenses(),
        ),
    )
    val ui: StateFlow<SuiviAdminUiState> = _ui.asStateFlow()

    val users: StateFlow<List<AppUser>> = userRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var loadJob: Job? = null

    init {
        if (_ui.value.canAccess) refresh()
    }

    fun selectTab(tab: SuiviTab) {
        _ui.update { it.copy(tab = tab, error = null, success = null) }
    }

    fun onPeriod(period: StatsPeriod) {
        _ui.update { it.copy(period = period) }
        refresh()
    }

    fun onCustomDay(ms: Long) {
        _ui.update { it.copy(customDayMs = ms, period = StatsPeriod.CUSTOM_DAY) }
        refresh()
    }

    fun onCustomRange(fromMs: Long, toMs: Long) {
        _ui.update {
            it.copy(
                customFromMs = fromMs,
                customToMs = toMs,
                period = StatsPeriod.CUSTOM_RANGE,
            )
        }
        refresh()
    }

    fun setFilterUser(userId: Long?) {
        _ui.update { it.copy(filterUserId = userId) }
        refresh()
    }

    fun setFilterMovementType(type: String?) {
        _ui.update { it.copy(filterMovementType = type) }
        refresh()
    }

    fun refresh() {
        if (!_ui.value.canAccess) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching {
                val state = _ui.value
                val (from, to) = DateRanges.boundsFor(
                    state.period,
                    customDayMs = state.customDayMs,
                    customFromMs = state.customFromMs,
                    customToMs = state.customToMs,
                )
                val movements = stockRepository.listMovements(
                    fromMs = from,
                    toMs = to,
                    userId = state.filterUserId,
                    type = state.filterMovementType,
                )
                val expenses = expenseRepository.listBetween(from, to)
                val total = expenseRepository.totalBetween(from, to)
                _ui.update {
                    it.copy(
                        movements = movements,
                        expenses = expenses,
                        expensesTotal = total,
                        loading = false,
                    )
                }
            }.onFailure { e ->
                _ui.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "Impossible de charger le suivi",
                    )
                }
            }
        }
    }

    fun openAddExpense() {
        if (!_ui.value.canManageExpenses) return
        _ui.update {
            it.copy(
                showAddExpense = true,
                expenseDescription = "",
                expenseAmountText = "",
                expenseCategory = ExpenseCategories.last(),
                error = null,
                success = null,
            )
        }
    }

    fun closeAddExpense() {
        _ui.update { it.copy(showAddExpense = false) }
    }

    fun setExpenseDescription(value: String) {
        _ui.update { it.copy(expenseDescription = value) }
    }

    fun setExpenseAmountText(value: String) {
        _ui.update { it.copy(expenseAmountText = value.filter { ch -> ch.isDigit() }) }
    }

    fun setExpenseCategory(value: String) {
        _ui.update { it.copy(expenseCategory = value) }
    }

    fun saveExpense() {
        if (!_ui.value.canManageExpenses) return
        val state = _ui.value
        val description = state.expenseDescription.trim()
        if (description.isBlank()) {
            _ui.update { it.copy(error = "Saisissez une description") }
            return
        }
        val amount = state.expenseAmountText.toLongOrNull()
        if (amount == null || amount <= 0L) {
            _ui.update { it.copy(error = "Montant invalide") }
            return
        }
        viewModelScope.launch {
            runCatching {
                expenseRepository.add(
                    description = description,
                    amount = amount,
                    category = state.expenseCategory.ifBlank { null },
                )
            }.onSuccess {
                _ui.update {
                    it.copy(
                        showAddExpense = false,
                        success = "Dépense enregistrée",
                        error = null,
                    )
                }
                refresh()
            }.onFailure { e ->
                _ui.update { it.copy(error = e.message ?: "Échec enregistrement dépense") }
            }
        }
    }

    fun consumeMessage() {
        _ui.update { it.copy(error = null, success = null) }
    }

    private fun canAccessReports(): Boolean {
        val user = sessionManager.userOrNull() ?: return false
        return user.role == "ADMIN" || user.can(Permissions.VIEW_REPORTS)
    }

    private fun canManageExpenses(): Boolean {
        val user = sessionManager.userOrNull() ?: return false
        return user.role == "ADMIN" || user.can(Permissions.MANAGE_EXPENSES)
    }
}
