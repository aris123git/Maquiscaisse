package com.maquis.caisse.ui.suivi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.model.Expense
import com.maquis.caisse.domain.model.ExpenseCategories
import com.maquis.caisse.domain.model.StatsPeriod
import com.maquis.caisse.domain.model.StockMovement
import com.maquis.caisse.domain.repository.ExpenseRepository
import com.maquis.caisse.domain.repository.OrderRepository
import com.maquis.caisse.domain.repository.StockRepository
import com.maquis.caisse.domain.repository.UserRepository
import com.maquis.caisse.ui.common.DateRanges
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MouvementsTab(val label: String, val movementType: String) {
    SORTIE("Sortie", "VENTE"),
    ENTREE("Entrée", "ENTREE"),
}

data class MouvementsUiState(
    val isAdmin: Boolean = false,
    /** null = tous les caissiers (admin seulement). */
    val selectedUserId: Long? = null,
    val period: StatsPeriod = StatsPeriod.TODAY,
    val customDayMs: Long = System.currentTimeMillis(),
    val customFromMs: Long = DateRanges.todayBounds().first,
    val customToMs: Long = DateRanges.todayBounds().second,
    val tab: MouvementsTab = MouvementsTab.SORTIE,
    val movements: List<StockMovement> = emptyList(),
    val ca: Long = 0L,
    val benefice: Long = 0L,
    val expensesTotal: Long = 0L,
    val expenses: List<Expense> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val success: String? = null,
    val showAddExpense: Boolean = false,
    val expenseDescription: String = "",
    val expenseAmountText: String = "",
    val expenseCategory: String = ExpenseCategories.last(),
)

@HiltViewModel
class SuiviAdminViewModel @Inject constructor(
    private val stockRepository: StockRepository,
    private val expenseRepository: ExpenseRepository,
    private val orderRepository: OrderRepository,
    userRepository: UserRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val currentUser = sessionManager.userOrNull()
    private val isAdmin = currentUser?.role == "ADMIN"

    private val _ui = MutableStateFlow(
        MouvementsUiState(
            isAdmin = isAdmin,
            selectedUserId = if (isAdmin) null else currentUser?.id,
        ),
    )
    val ui: StateFlow<MouvementsUiState> = _ui.asStateFlow()

    val users: StateFlow<List<AppUser>> = userRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var loadJob: Job? = null
    private var loadSeq = 0

    init {
        refresh()
    }

    fun selectTab(tab: MouvementsTab) {
        // Vide tout de suite pour éviter un état hybride Sortie/Entrée pendant le chargement.
        _ui.update {
            it.copy(
                tab = tab,
                movements = emptyList(),
                expenses = emptyList(),
                error = null,
                success = null,
            )
        }
        refresh()
    }

    fun setSelectedUser(userId: Long?) {
        if (!isAdmin) return
        _ui.update { it.copy(selectedUserId = userId) }
        refresh()
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

    fun refresh() {
        loadJob?.cancel()
        val seq = ++loadSeq
        loadJob = viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val state = _ui.value
                val (from, to) = DateRanges.boundsFor(
                    state.period,
                    customDayMs = state.customDayMs,
                    customFromMs = state.customFromMs,
                    customToMs = state.customToMs,
                )
                val userId = state.selectedUserId
                val tab = state.tab

                val raw = stockRepository.listMovementsByType(
                    type = tab.movementType,
                    fromMs = from,
                    toMs = to,
                    userId = userId,
                )
                // Dédupe défensif (évite crash LazyColumn sur clé id).
                val movements = raw
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAtEpochMs }

                // Stats financières uniquement sur Sortie (Entrée n'en a pas besoin).
                var ca = 0L
                var benefice = 0L
                var expensesTotal = 0L
                var expenses: List<Expense> = emptyList()
                if (tab == MouvementsTab.SORTIE) {
                    val stats = orderRepository.cashierPeriodStats(from, to, userId)
                    ca = stats.ca
                    benefice = stats.benefice
                    expensesTotal = if (userId != null) {
                        expenseRepository.totalByUserAndDateRange(userId, from, to)
                    } else {
                        expenseRepository.totalBetween(from, to)
                    }
                    expenses = if (userId != null) {
                        expenseRepository.listByUserAndDateRange(userId, from, to)
                    } else {
                        expenseRepository.listBetween(from, to)
                    }
                }

                if (seq != loadSeq) return@launch
                _ui.update {
                    it.copy(
                        movements = movements,
                        ca = ca,
                        benefice = benefice,
                        expensesTotal = expensesTotal,
                        expenses = expenses,
                        loading = false,
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (seq != loadSeq) return@launch
                _ui.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "Impossible de charger les mouvements",
                    )
                }
            }
        }
    }

    fun openAddExpense() {
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
            try {
                expenseRepository.add(
                    description = description,
                    amount = amount,
                    category = state.expenseCategory.ifBlank { null },
                )
                _ui.update {
                    it.copy(
                        showAddExpense = false,
                        success = "Dépense enregistrée",
                        error = null,
                    )
                }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.message ?: "Échec enregistrement dépense") }
            }
        }
    }

    fun consumeMessage() {
        _ui.update { it.copy(error = null, success = null) }
    }
}
