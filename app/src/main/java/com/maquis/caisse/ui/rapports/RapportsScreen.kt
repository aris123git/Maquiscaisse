package com.maquis.caisse.ui.rapports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.model.Category
import com.maquis.caisse.domain.model.CategorySalesRow
import com.maquis.caisse.domain.model.DashboardStats
import com.maquis.caisse.domain.model.OrderStatus
import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.domain.model.ProductSalesRow
import com.maquis.caisse.domain.model.WaitressStats
import com.maquis.caisse.domain.repository.CategoryRepository
import com.maquis.caisse.domain.repository.OrderRepository
import com.maquis.caisse.domain.repository.UserRepository
import com.maquis.caisse.ui.commandes.HistoryPeriod
import com.maquis.caisse.ui.common.DateRanges
import com.maquis.caisse.ui.common.DropdownField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RapportsUiState(
    val period: HistoryPeriod = HistoryPeriod.TODAY,
    val waitressId: Long? = null,
    val category: String? = null,
    val status: OrderStatus? = null,
    val paymentMode: PaymentMode? = null,
    val dashboard: DashboardStats? = null,
    val waitressStats: List<WaitressStats> = emptyList(),
    val categories: List<CategorySalesRow> = emptyList(),
    val products: List<ProductSalesRow> = emptyList(),
)

@HiltViewModel
class RapportsViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    userRepository: UserRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(RapportsUiState())
    val ui: StateFlow<RapportsUiState> = _ui.asStateFlow()

    val waitresses: StateFlow<List<AppUser>> = userRepository.observeWaitresses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categoryOptions: StateFlow<List<Category>> = categoryRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    fun onPeriod(p: HistoryPeriod) {
        _ui.update { it.copy(period = p) }
        refresh()
    }

    fun onWaitress(id: Long?) {
        _ui.update { it.copy(waitressId = id) }
        refresh()
    }

    fun onCategory(name: String?) {
        _ui.update { it.copy(category = name) }
        refresh()
    }

    fun onStatus(s: OrderStatus?) {
        _ui.update { it.copy(status = s) }
        refresh()
    }

    fun onPayment(m: PaymentMode?) {
        _ui.update { it.copy(paymentMode = m) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val state = _ui.value
            val (from, to) = when (state.period) {
                HistoryPeriod.TODAY -> DateRanges.todayBounds()
                HistoryPeriod.WEEK -> DateRanges.lastDaysBounds(7)
                HistoryPeriod.MONTH -> DateRanges.lastDaysBounds(30)
                HistoryPeriod.ALL -> DateRanges.allTimeBounds()
            }
            val dash = orderRepository.dashboard(from, to)
            val wStats = orderRepository.waitressStats(from, to, state.waitressId)
            var cats = orderRepository.categorySales(from, to, state.waitressId)
            var products = orderRepository.productSales(from, to, state.waitressId)
            state.category?.let { c ->
                cats = cats.filter { it.categoryName.equals(c, true) }
                products = products.filter { it.categoryName.equals(c, true) }
            }
            _ui.update {
                it.copy(
                    dashboard = dash,
                    waitressStats = wStats,
                    categories = cats,
                    products = products.take(50),
                )
            }
        }
    }
}

@Composable
fun RapportsScreen(viewModel: RapportsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val waitresses by viewModel.waitresses.collectAsStateWithLifecycle()
    val categories by viewModel.categoryOptions.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Rapports", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = viewModel::refresh) { Text("Actualiser") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DropdownField(
                label = "Période",
                selected = ui.period,
                options = HistoryPeriod.entries,
                optionLabel = {
                    when (it) {
                        HistoryPeriod.TODAY -> "Aujourd'hui"
                        HistoryPeriod.WEEK -> "7 jours"
                        HistoryPeriod.MONTH -> "30 jours"
                        HistoryPeriod.ALL -> "Tout"
                    }
                },
                onSelect = { it?.let(viewModel::onPeriod) },
                modifier = Modifier.weight(1f),
            )
            DropdownField(
                label = "Serveuse",
                selected = waitresses.firstOrNull { it.id == ui.waitressId },
                options = waitresses,
                optionLabel = { it.name },
                onSelect = { viewModel.onWaitress(it?.id) },
                allowNull = true,
                nullLabel = "Toutes",
                modifier = Modifier.weight(1f),
            )
            DropdownField(
                label = "Catégorie",
                selected = ui.category,
                options = categories.map { it.name },
                optionLabel = { it },
                onSelect = viewModel::onCategory,
                allowNull = true,
                nullLabel = "Toutes",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DropdownField(
                label = "Statut",
                selected = ui.status,
                options = OrderStatus.entries,
                optionLabel = { it.label },
                onSelect = viewModel::onStatus,
                allowNull = true,
                nullLabel = "Tous",
                modifier = Modifier.weight(1f),
            )
            DropdownField(
                label = "Paiement",
                selected = ui.paymentMode,
                options = PaymentMode.PAYMENT_CHOICES,
                optionLabel = { it.label },
                onSelect = viewModel::onPayment,
                allowNull = true,
                nullLabel = "Tous",
                modifier = Modifier.weight(1f),
            )
        }

        val d = ui.dashboard
        if (d != null) {
            Text("CA généré : ${MoneyFormat.format(d.caGenerated)}", fontWeight = FontWeight.Bold)
            Text("CA encaissé : ${MoneyFormat.format(d.caCollected)}", fontWeight = FontWeight.Bold)
            Text("Reste à encaisser : ${MoneyFormat.format(d.toCollect)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Nombre de commandes : ${d.ordersToday}")
        }

        Text("Performance des serveuses", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        ui.waitressStats.forEach {
            Text(
                "${it.waitressName} · cmd ${it.orderCount} · généré ${MoneyFormat.format(it.caGenerated)} · " +
                    "encaissé ${MoneyFormat.format(it.caCollected)} · à encaisser ${MoneyFormat.format(it.toCollect)}",
            )
        }

        Text("Catégories vendues", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        ui.categories.forEach {
            Text("${it.categoryName} · ${it.quantity} · ${MoneyFormat.format(it.revenue)}")
        }

        Text("Produits vendus", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        ui.products.forEach {
            Text("${it.productName} (${it.categoryName}) · ${it.quantity} · ${MoneyFormat.format(it.revenue)}")
        }
    }
}
