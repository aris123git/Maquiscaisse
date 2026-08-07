package com.maquis.caisse.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.domain.model.BilanJourStats
import com.maquis.caisse.domain.model.DashboardStats
import com.maquis.caisse.domain.repository.AvoirRepository
import com.maquis.caisse.domain.repository.DetteRepository
import com.maquis.caisse.domain.repository.OrderRepository
import com.maquis.caisse.ui.common.DateRanges
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private val MOBILE_KEYS = setOf(
    "ORANGE_MONEY", "MOOV_MONEY", "WAVE", "CARD",
    "MOBILE_MONEY", "VOUCHER", "TRANSFER",   // legacy keys
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val detteRepository: DetteRepository,
    private val avoirRepository: AvoirRepository,
) : ViewModel() {

    private val _stats = MutableStateFlow<DashboardStats?>(null)
    val stats: StateFlow<DashboardStats?> = _stats.asStateFlow()

    private val _bilan = MutableStateFlow<BilanJourStats?>(null)
    val bilan: StateFlow<BilanJourStats?> = _bilan.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val (from, to) = DateRanges.todayBounds()

            // Chargements parallèles
            val dashDeferred  = async { orderRepository.dashboard(from, to) }
            val bilanDeferred = async { orderRepository.bilanJour(from, to) }
            val dettesDeferred = async { detteRepository.observeOpen().first() }
            val avoirsDeferred = async { avoirRepository.observeAll().first() }

            _stats.value = dashDeferred.await()

            val breakdown   = bilanDeferred.await()
            val openDettes  = dettesDeferred.await()
            val allAvoirs   = avoirsDeferred.await()
            val avoirToday  = allAvoirs.filter { it.createdAt in from..to }.sumOf { it.amount }

            val cash   = breakdown["CASH"] ?: 0L
            val mobile = MOBILE_KEYS.sumOf { breakdown[it] ?: 0L }
            val debt   = breakdown["DEBT"] ?: 0L
            val other  = breakdown["OTHER"] ?: 0L

            _bilan.value = BilanJourStats(
                cashSales          = cash,
                mobileSales        = mobile,
                debtSales          = debt,
                otherSales         = other,
                dettesOuvertesCount = openDettes.size,
                dettesOuvertesTotal = openDettes.sumOf { it.remainingAmount },
                avoirsTotal        = avoirToday,
            )
        }
    }
}
