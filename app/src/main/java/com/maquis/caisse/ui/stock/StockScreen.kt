package com.maquis.caisse.ui.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.domain.model.Product
import com.maquis.caisse.domain.model.StockMovement
import com.maquis.caisse.domain.repository.StockRepository
import com.maquis.caisse.domain.usecase.ObserveProductsUseCase
import com.maquis.caisse.ui.common.DropdownField
import com.maquis.caisse.ui.common.PageHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

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
        inventaireStock: Int?,
    ) = viewModelScope.launch {
        if (product == null) {
            _message.value = "Choisis un produit"
            return@launch
        }
        try {
            stockRepository.adjust(
                productId = product.id,
                type = type,
                quantity = qty,
                motif = motif.ifBlank { type },
                supplier = supplier?.ifBlank { null },
                comment = comment?.ifBlank { null },
                absoluteNewStock = inventaireStock,
            )
            _message.value = "Mouvement enregistré"
        } catch (e: Exception) {
            _message.value = e.message
        }
    }
}

@Composable
fun StockScreen(viewModel: StockViewModel = hiltViewModel()) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val movements by viewModel.movements.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Product?>(null) }
    var type by remember { mutableStateOf("ENTREE") }
    var qty by remember { mutableStateOf("1") }
    var motif by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var inventaire by remember { mutableStateOf("") }
    val df = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE) }

    Row(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PageHeader(title = "Stock", subtitle = "Mouvements et alertes")
            DropdownField(
                label = "Produit",
                selected = selected,
                options = products,
                optionLabel = { "${it.name} (stock ${it.stock})" },
                onSelect = { selected = it },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("ENTREE", "SORTIE", "CORRECTION", "INVENTAIRE").forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                }
            }
            if (type != "INVENTAIRE") {
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it.filter { c -> c.isDigit() } },
                    label = { Text("Quantité") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = inventaire,
                    onValueChange = { inventaire = it.filter { c -> c.isDigit() } },
                    label = { Text("Nouveau stock (inventaire)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(value = motif, onValueChange = { motif = it }, label = { Text("Motif") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = supplier, onValueChange = { supplier = it }, label = { Text("Fournisseur (optionnel)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Commentaire") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    viewModel.adjust(
                        product = selected,
                        type = type,
                        qty = qty.toIntOrNull() ?: 0,
                        motif = motif,
                        supplier = supplier,
                        comment = comment,
                        inventaireStock = if (type == "INVENTAIRE") inventaire.toIntOrNull() else null,
                    )
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Enregistrer le mouvement") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Text(
                "Alertes : produits sous seuil affichés en rouge dans la liste.",
                style = MaterialTheme.typography.bodyMedium,
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(products.filter { it.stock <= it.alertThreshold }, key = { it.id }) { p ->
                    Text(
                        "${p.name} : ${p.stock} (seuil ${p.alertThreshold})",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1.2f)) {
            Text("Historique des mouvements", style = MaterialTheme.typography.titleLarge)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(movements, key = { it.id }) { m ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(df.format(Date(m.createdAtEpochMs)), style = MaterialTheme.typography.labelLarge)
                        Text("${m.userName ?: "—"} · ${m.productName}")
                        Text(
                            "${m.type} ${if (m.type == "ENTREE") "+" else ""}${m.quantity} · " +
                                "Stock ${m.previousStock} → ${m.newStock}",
                        )
                        if (m.motif.isNotBlank()) Text(m.motif, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!m.supplier.isNullOrBlank()) Text("Fournisseur : ${m.supplier}")
                        if (!m.comment.isNullOrBlank()) Text(m.comment!!)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
