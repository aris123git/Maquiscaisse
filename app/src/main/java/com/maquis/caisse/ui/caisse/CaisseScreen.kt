package com.maquis.caisse.ui.caisse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.core.Constants
import com.maquis.caisse.ui.common.DropdownField
import com.maquis.caisse.ui.common.PageHeader
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import com.maquis.caisse.ui.components.NumericKeypad
import com.maquis.caisse.ui.produits.ProductTile

/**
 * Caisse paysage type Gestion_app : catalogue à gauche, panier compact à droite.
 */
@Composable
fun CaisseScreen(
    onOrderCreated: (Long) -> Unit = {},
    viewModel: CaisseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        val message = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeSnackbar()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Catalogue (gauche) — élargi pour compenser panier réduit
            Column(
                modifier = Modifier
                    .weight(1.85f)
                    .fillMaxHeight()
                    .padding(10.dp),
            ) {
                PageHeader(
                    title = "Caisse",
                    subtitle = "Touche un produit pour l'ajouter au panier",
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                if (state.cart.isNotEmpty()) {
                    TextPill(
                        "${state.cart.sumOf { it.quantity }} dans le panier · ${MoneyFormat.format(state.cartTotal)}",
                        PillTone.INFO,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DropdownField(
                        label = "Serveuse",
                        selected = state.selectedWaitress,
                        options = state.waitresses,
                        optionLabel = { it.name },
                        onSelect = viewModel::selectWaitress,
                        allowNull = true,
                        nullLabel = "Aucune",
                        modifier = Modifier.weight(1f),
                    )
                    if (state.tablesEnabled) {
                        DropdownField(
                            label = "Table",
                            selected = state.selectedTable,
                            options = state.tables,
                            optionLabel = { it.label },
                            onSelect = viewModel::selectTable,
                            allowNull = true,
                            nullLabel = "Aucune",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    singleLine = true,
                    placeholder = { Text("Rechercher un produit…") },
                )

                if (state.filteredProducts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (state.products.isEmpty()) {
                                "Aucun produit actif.\nAjoute-en dans Produits."
                            } else {
                                "Aucun résultat"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = Constants.PRODUCT_TILE_MIN_DP.dp),
                        contentPadding = PaddingValues(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        items(state.filteredProducts, key = { it.id }) { product ->
                            ProductTile(
                                product = product,
                                imageFile = viewModel.imageFile(product.imagePath),
                                onClick = { viewModel.onProductTap(product) },
                            )
                        }
                    }
                }
            }

            // Panier (droite) — style verre
            CartPanel(
                lines = state.cart,
                total = state.cartTotal,
                onLineLongPress = viewModel::onCartLineLongPress,
                onValidate = viewModel::openPayment,
                onClear = viewModel::clearCart,
                onSaveOrder = {
                    viewModel.saveUnpaidOrder(onCreated = onOrderCreated)
                },
                onReprint = if (state.completedOrder != null) viewModel::printLastOrder else null,
                modifier = Modifier
                    .weight(0.72f)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    state.quantityOverlay?.let { overlay ->
        Dialog(
            onDismissRequest = viewModel::dismissQuantityOverlay,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = true,
            ),
        ) {
            Surface(modifier = Modifier.fillMaxWidth(0.45f)) {
                NumericKeypad(
                    value = overlay.quantityInput,
                    onValueChange = viewModel::onQuantityInputChange,
                    onConfirm = viewModel::confirmQuantity,
                    title = overlay.productName,
                    subtitle = MoneyFormat.format(overlay.unitPrice),
                    maxDigits = Constants.MAX_QUANTITY_DIGITS,
                    confirmLabel = "OK",
                    confirmEnabled = overlay.quantityInput.isNotEmpty(),
                    inputSessionKey = "${overlay.productId}-${overlay.isEditing}",
                    onDeleteLine = if (overlay.isEditing) {
                        { viewModel.deleteOverlayLine() }
                    } else {
                        null
                    },
                )
            }
        }
    }

    if (state.showPayment) {
        PaymentDialog(
            total = state.cartTotal,
            payment = state.payment,
            onDismiss = viewModel::dismissPayment,
            onSelectMode = viewModel::selectPaymentMode,
            onSelectField = viewModel::selectPaymentField,
            onInputChange = viewModel::onPaymentDigitInput,
            onConfirm = viewModel::confirmPayment,
        )
    }

    state.completedSale?.let { sale ->
        TicketDialog(
            sale = sale,
            onDismiss = viewModel::dismissTicket,
        )
    }
}
