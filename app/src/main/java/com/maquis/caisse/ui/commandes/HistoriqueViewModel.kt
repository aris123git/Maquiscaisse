package com.maquis.caisse.ui.commandes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.model.Category
import com.maquis.caisse.domain.model.CategorySalesRow
import com.maquis.caisse.domain.model.ChartPoint
import com.maquis.caisse.domain.model.ChartType
import com.maquis.caisse.domain.model.Order
import com.maquis.caisse.domain.model.OrderStatus
import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.domain.model.ProductSalesRow
import com.maquis.caisse.domain.model.StatsPeriod
import com.maquis.caisse.domain.model.WaitressStats
import com.maquis.caisse.domain.repository.CategoryRepository
import com.maquis.caisse.domain.repository.OrderRepository
import com.maquis.caisse.domain.repository.UserRepository
import com.maquis.caisse.ui.common.DateRanges
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoriqueUiState(
    val query: String = "",
    val status: OrderStatus? = null,
    val waitressId: Long? = null,
    val paymentMode: PaymentMode? = null,
    val categoryFilter: String? = null,
    val period: StatsPeriod = StatsPeriod.TODAY,
    val customDayMs: Long = System.currentTimeMillis(),
    val customFromMs: Long = DateRanges.todayBounds().first,
    val customToMs: Long = DateRanges.todayBounds().second,
    val waitresses: List<AppUser> = emptyList(),
    val categories: List<String> = emptyList(),
    val waitressStats: WaitressStats? = null,
    val categoryRows: List<CategorySalesRow> = emptyList(),
    val productRows: List<ProductSalesRow> = emptyList(),
    val timeSeries: List<ChartPoint> = emptyList(),
    val allWaitressStats: List<WaitressStats> = emptyList(),
    val salesChartType: ChartType = ChartType.CURVE,
    val categoriesChartType: ChartType = ChartType.PIE,
    val productsChartType: ChartType = ChartType.BAR_HORIZONTAL,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoriqueViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val userRepository: UserRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(HistoriqueUiState())
    val ui: StateFlow<HistoriqueUiState> = _ui.asStateFlow()

    val orders: StateFlow<List<Order>> = _ui
        .flatMapLatest { state ->
            val (from, to) = bounds(state)
            orderRepository.observeFiltered(
                query = state.query,
                status = state.status,
                waitressId = state.waitressId,
                fromMs = from,
                toMs = to,
            ).map { list ->
                if (state.paymentMode == null) list
                else list
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            userRepository.observeWaitresses().collect { list ->
                _ui.update { it.copy(waitresses = list) }
            }
        }
        viewModelScope.launch {
            categoryRepository.observeActive().collect { list ->
                _ui.update { it.copy(categories = list.map(Category::name)) }
            }
        }
        refreshStats()
    }

    fun onQuery(q: String) {
        _ui.update { it.copy(query = q) }
        refreshStats()
    }

    fun onStatus(s: OrderStatus?) {
        _ui.update { it.copy(status = s) }
    }

    fun onWaitress(id: Long?) {
        _ui.update { it.copy(waitressId = id) }
        refreshStats()
    }

    fun onPeriod(p: StatsPeriod) {
        _ui.update { it.copy(period = p) }
        refreshStats()
    }

    fun onCustomDay(ms: Long) {
        _ui.update { it.copy(customDayMs = ms, period = StatsPeriod.CUSTOM_DAY) }
        refreshStats()
    }

    fun onCustomRange(fromMs: Long, toMs: Long) {
        _ui.update {
            it.copy(customFromMs = fromMs, customToMs = toMs, period = StatsPeriod.CUSTOM_RANGE)
        }
        refreshStats()
    }

    fun onPaymentMode(m: PaymentMode?) {
        _ui.update { it.copy(paymentMode = m) }
        refreshStats()
    }

    fun onCategory(c: String?) {
        _ui.update { it.copy(categoryFilter = c) }
        refreshStats()
    }

    fun onSalesChartType(t: ChartType) = _ui.update { it.copy(salesChartType = t) }
    fun onCategoriesChartType(t: ChartType) = _ui.update { it.copy(categoriesChartType = t) }
    fun onProductsChartType(t: ChartType) = _ui.update { it.copy(productsChartType = t) }

    fun refreshStats() {
        viewModelScope.launch {
            val state = _ui.value
            val (from, to) = bounds(state)
            val grain = DateRanges.grainFor(state.period, from, to)
            val statsList = orderRepository.waitressStats(from, to, state.waitressId)
            val stats = if (state.waitressId != null) {
                statsList.firstOrNull { it.waitressId == state.waitressId }
            } else if (statsList.isEmpty()) {
                null
            } else {
                WaitressStats(
                    waitressId = null,
                    waitressName = "Toutes",
                    orderCount = statsList.sumOf { it.orderCount },
                    paidCount = statsList.sumOf { it.paidCount },
                    unpaidCount = statsList.sumOf { it.unpaidCount },
                    caGenerated = statsList.sumOf { it.caGenerated },
                    caCollected = statsList.sumOf { it.caCollected },
                    toCollect = statsList.sumOf { it.toCollect },
                )
            }
            val cats = orderRepository.categorySales(from, to, state.waitressId)
                .let { rows ->
                    val cat = state.categoryFilter
                    if (cat == null) rows else rows.filter { it.categoryName.equals(cat, true) }
                }
            val products = orderRepository.productSales(from, to, state.waitressId)
                .let { rows ->
                    val cat = state.categoryFilter
                    if (cat == null) rows else rows.filter { it.categoryName.equals(cat, true) }
                }
            _ui.update {
                it.copy(
                    waitressStats = stats,
                    categoryRows = cats,
                    productRows = products.take(40),
                    allWaitressStats = statsList,
                    timeSeries = orderRepository.salesTimeSeries(from, to, grain, state.waitressId),
                )
            }
        }
    }

    fun filterDisplayed(orders: List<Order>): List<Order> = orders

    private fun bounds(state: HistoriqueUiState): Pair<Long, Long> =
        DateRanges.boundsFor(
            state.period,
            customDayMs = state.customDayMs,
            customFromMs = state.customFromMs,
            customToMs = state.customToMs,
        )
}
