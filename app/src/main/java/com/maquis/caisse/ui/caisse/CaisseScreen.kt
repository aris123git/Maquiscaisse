package com.maquis.caisse.ui.caisse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.maquis.caisse.ui.components.NumericKeypad
import com.maquis.caisse.ui.produits.ProductTile

/**
 * Écran Caisse (Sprint 2) : grille → quantité → panier → paiement → ticket.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaisseScreen(
    viewModel: CaisseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        val message = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeSnackbar()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Caisse") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Aucun produit actif.\nAjoute-en dans l'onglet Produits.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(Constants.PRODUCT_GRID_COLUMNS),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    items(state.products, key = { it.id }) { product ->
                        ProductTile(
                            product = product,
                            imageFile = viewModel.imageFile(product.imagePath),
                            onClick = { viewModel.onProductTap(product) },
                        )
                    }
                }
            }

            CartPanel(
                lines = state.cart,
                total = state.cartTotal,
                onLineLongPress = viewModel::onCartLineLongPress,
                onValidate = viewModel::openPayment,
            )
        }
    }

    state.quantityOverlay?.let { overlay ->
        Dialog(
            onDismissRequest = viewModel::dismissQuantityOverlay,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = true,
            ),
        ) {
            Surface(modifier = Modifier.fillMaxWidth()) {
                NumericKeypad(
                    value = overlay.quantityInput,
                    onValueChange = viewModel::onQuantityInputChange,
                    onConfirm = viewModel::confirmQuantity,
                    title = overlay.productName,
                    subtitle = MoneyFormat.format(overlay.unitPrice),
                    maxDigits = Constants.MAX_QUANTITY_DIGITS,
                    confirmLabel = "OK",
                    confirmEnabled = overlay.quantityInput.isNotEmpty(),
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
