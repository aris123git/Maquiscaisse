package com.maquis.caisse.ui.stock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.domain.model.Product
import com.maquis.caisse.domain.model.StockMovement
import com.maquis.caisse.domain.model.StockMovementType
import com.maquis.caisse.domain.repository.StockRepository
import com.maquis.caisse.domain.usecase.ObserveProductsUseCase
import com.maquis.caisse.ui.common.DropdownField
import com.maquis.caisse.ui.common.GlassCard
import com.maquis.caisse.ui.common.PageHeader
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class StockViewModel @Inject constructor(
    observeProducts: ObserveProductsUseCase,
    private val stockRepository: StockRepository,
) : ViewModel() {
    val products: StateFlow<List<Product>> = observeProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val movements: StateFlow<List<StockMovement>> = stockRepository.observeMovements(300)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun adjust(
        product: Product?,
        type: String,
        qty: Int,
        motif: String,
        supplier: String?,
        comment: String?,
    ) = viewModelScope.launch {
        if (product == null) {
            _message.value = "Choisis un produit"
            return@launch
        }
        if (qty <= 0) {
            _message.value = "Quantité invalide"
            return@launch
        }
        if (motif.isBlank()) {
            _message.value = "Motif obligatoire"
            return@launch
        }
        try {
            stockRepository.adjust(
                productId = product.id,
                type = type,
                quantity = qty,
                motif = motif,
                supplier = supplier?.ifBlank { null },
                comment = comment?.ifBlank { null },
            )
            _message.value = "Mouvement enregistré"
        } catch (e: Exception) {
            _message.value = e.message
        }
    }
}

@Composable
fun StockScreen(
    onOpenInventaire: () -> Unit = {},
    viewModel: StockViewModel = hiltViewModel(),
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val movements by viewModel.movements.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Product?>(null) }
    var type by remember { mutableStateOf(StockMovementType.ENTREE) }
    var qty by remember { mutableStateOf("1") }
    var motif by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    val df = remember { SimpleDateFormat("HH:mm", Locale.FRANCE) }
    val dayFmt = remember { SimpleDateFormat("dd/MM", Locale.FRANCE) }

    Row(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PageHeader(title = "Stock", subtitle = "Entrées · Sorties · Pertes")
            OutlinedButton(
                onClick = onOpenInventaire,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Inventaire tactile")
            }
            TextPill("${products.size} produits", PillTone.INFO)
            GlassCard {
                DropdownField(
                    label = "Produit",
                    selected = selected,
                    options = products,
                    optionLabel = { "${it.name} (stock ${it.stock})" },
                    onSelect = { selected = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        StockMovementType.ENTREE to "Entrée",
                        StockMovementType.SORTIE to "Sortie",
                        StockMovementType.PERTE to "Perte",
                    ).forEach { (key, label) ->
                        val selectedType = type == key
                        TextPill(
                            label,
                            if (selectedType) {
                                when (key) {
                                    StockMovementType.ENTREE -> PillTone.SUCCESS
                                    StockMovementType.SORTIE -> PillTone.WARNING
                                    else -> PillTone.DANGER
                                }
                            } else {
                                PillTone.NEUTRAL
                            },
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clickable {
                                    type = key
                                    if (key == StockMovementType.PERTE && motif.isBlank()) {
                                        motif = StockMovementType.PERTE_MOTIFS.first()
                                    }
                                },
                        )
                    }
                }
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it.filter { c -> c.isDigit() } },
                    label = { Text("Quantité") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type == StockMovementType.PERTE) {
                    DropdownField(
                        label = "Motif perte",
                        selected = motif.ifBlank { null },
                        options = StockMovementType.PERTE_MOTIFS,
                        optionLabel = { it },
                        onSelect = { motif = it.orEmpty() },
                    )
                } else {
                    OutlinedTextField(
                        value = motif,
                        onValueChange = { motif = it },
                        label = { Text("Motif") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (type == StockMovementType.ENTREE) {
                    OutlinedTextField(
                        value = supplier,
                        onValueChange = { supplier = it },
                        label = { Text("Fournisseur (optionnel)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Commentaire") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        viewModel.adjust(
                            product = selected,
                            type = type,
                            qty = qty.toIntOrNull() ?: 0,
                            motif = motif,
                            supplier = supplier,
                            comment = comment,
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text("Enregistrer") }
                message?.let { TextPill(it, PillTone.SUCCESS) }
            }

            val alerts = products.filter { it.stock <= it.alertThreshold }
            TextPill(
                "${alerts.size} alertes stock",
                if (alerts.isEmpty()) PillTone.SUCCESS else PillTone.DANGER,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(alerts, key = { it.id }) { p ->
                    GlassCard {
                        Text(p.name, fontWeight = FontWeight.SemiBold)
                        TextPill("Stock ${p.stock} · seuil ${p.alertThreshold}", PillTone.DANGER)
                    }
                }
            }
        }

        Column(
            modifier = Modifier.weight(1.2f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Mouvements récents",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(movements, key = { it.id }) { m ->
                    MovementTimelineCard(m, df, dayFmt)
                }
            }
        }
    }
}

@Composable
fun MovementTimelineCard(
    m: StockMovement,
    timeFmt: SimpleDateFormat,
    dayFmt: SimpleDateFormat,
    onClick: (() -> Unit)? = null,
) {
    val tone = when (m.type) {
        StockMovementType.ENTREE -> PillTone.SUCCESS
        StockMovementType.VENTE -> PillTone.INFO
        StockMovementType.PERTE -> PillTone.DANGER
        StockMovementType.SORTIE -> PillTone.WARNING
        StockMovementType.INVENTAIRE -> PillTone.CYAN
        else -> PillTone.NEUTRAL
    }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Text(
            "${dayFmt.format(Date(m.createdAtEpochMs))} — ${timeFmt.format(Date(m.createdAtEpochMs))}",
            style = MaterialTheme.typography.labelLarge,
        )
        TextPill(StockMovementType.label(m.type), tone)
        Text(m.productName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        Text(
            StockMovementType.signedQuantity(m.type, m.quantity),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )
        Text("Stock après : ${m.newStock}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (m.motif.isNotBlank()) {
            Text("Motif : ${m.motif}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        m.userName?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
    }
}
