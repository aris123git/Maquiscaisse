package com.maquis.caisse.ui.produits

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.domain.model.Product
import com.maquis.caisse.domain.usecase.AddProductUseCase
import com.maquis.caisse.domain.usecase.DeleteProductUseCase
import com.maquis.caisse.domain.usecase.ObserveProductsUseCase
import com.maquis.caisse.domain.usecase.ResolveProductImageUseCase
import com.maquis.caisse.domain.usecase.UpdateProductUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ProductFormState(
    val editingId: Long? = null,
    val name: String = "",
    val category: String = "",
    val salePrice: String = "",
    val purchasePrice: String = "",
    val stock: String = "0",
    val alertThreshold: String = "5",
    val isActive: Boolean = true,
    /** Image déjà persistée (édition). */
    val existingImagePath: String? = null,
    /** Nouvelle image choisie (galerie/caméra), pas encore sauvegardée. */
    val pendingImageUri: Uri? = null,
    val clearExistingImage: Boolean = false,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
)

data class ProduitsUiState(
    val products: List<Product> = emptyList(),
    val form: ProductFormState? = null,
    val snackbarMessage: String? = null,
)

@HiltViewModel
class ProduitsViewModel @Inject constructor(
    observeProducts: ObserveProductsUseCase,
    private val addProduct: AddProductUseCase,
    private val updateProduct: UpdateProductUseCase,
    private val deleteProduct: DeleteProductUseCase,
    private val resolveImage: ResolveProductImageUseCase,
) : ViewModel() {

    private val productsFlow: StateFlow<List<Product>> = observeProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(ProduitsUiState())
    val uiState: StateFlow<ProduitsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            productsFlow.collect { products ->
                _uiState.update { it.copy(products = products) }
            }
        }
    }

    fun imageFile(relativePath: String?): File? = resolveImage(relativePath)

    fun openCreateForm() {
        _uiState.update { it.copy(form = ProductFormState()) }
    }

    fun openEditForm(product: Product) {
        _uiState.update {
            it.copy(
                form = ProductFormState(
                    editingId = product.id,
                    name = product.name,
                    category = product.category,
                    salePrice = product.salePrice.toString(),
                    purchasePrice = product.purchasePrice.toString(),
                    stock = product.stock.toString(),
                    alertThreshold = product.alertThreshold.toString(),
                    isActive = product.isActive,
                    existingImagePath = product.imagePath,
                ),
            )
        }
    }

    fun closeForm() {
        _uiState.update { it.copy(form = null) }
    }

    fun updateForm(transform: (ProductFormState) -> ProductFormState) {
        _uiState.update { state ->
            val form = state.form ?: return@update state
            state.copy(form = transform(form).copy(errorMessage = null))
        }
    }

    fun onImagePicked(uri: Uri) {
        updateForm {
            it.copy(
                pendingImageUri = uri,
                clearExistingImage = false,
            )
        }
    }

    fun onClearImage() {
        updateForm {
            it.copy(
                pendingImageUri = null,
                existingImagePath = null,
                clearExistingImage = true,
            )
        }
    }

    fun saveForm() {
        val form = _uiState.value.form ?: return
        val name = form.name.trim()
        if (name.isEmpty()) {
            updateForm { it.copy(errorMessage = "Le nom est obligatoire") }
            return
        }
        val salePrice = form.salePrice.toLongOrNull()
        val purchasePrice = form.purchasePrice.toLongOrNull() ?: 0L
        val stock = form.stock.toIntOrNull()
        val alert = form.alertThreshold.toIntOrNull()
        if (salePrice == null || salePrice <= 0) {
            updateForm { it.copy(errorMessage = "Prix de vente invalide (doit être > 0)") }
            return
        }
        if (purchasePrice < 0) {
            updateForm { it.copy(errorMessage = "Prix d'achat invalide") }
            return
        }
        if (stock == null || stock < 0) {
            updateForm { it.copy(errorMessage = "Stock invalide") }
            return
        }
        if (alert == null || alert < 0) {
            updateForm { it.copy(errorMessage = "Seuil d'alerte invalide") }
            return
        }

        val product = Product(
            id = form.editingId ?: 0L,
            name = name,
            category = form.category.trim().ifEmpty { "Divers" },
            salePrice = salePrice,
            purchasePrice = purchasePrice,
            stock = stock,
            alertThreshold = alert,
            imagePath = form.existingImagePath,
            isActive = form.isActive,
        )

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(form = state.form?.copy(isSaving = true, errorMessage = null))
            }
            try {
                if (form.editingId == null) {
                    addProduct(product, form.pendingImageUri)
                    _uiState.update {
                        it.copy(form = null, snackbarMessage = "Produit ajouté")
                    }
                } else {
                    updateProduct(
                        product = product,
                        newImageUri = form.pendingImageUri,
                        clearImage = form.clearExistingImage && form.pendingImageUri == null,
                    )
                    _uiState.update {
                        it.copy(form = null, snackbarMessage = "Produit mis à jour")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        form = state.form?.copy(
                            isSaving = false,
                            errorMessage = e.message ?: "Erreur d'enregistrement",
                        ),
                    )
                }
            }
        }
    }

    fun deleteCurrent() {
        val id = _uiState.value.form?.editingId ?: return
        viewModelScope.launch {
            try {
                deleteProduct(id)
                _uiState.update {
                    it.copy(form = null, snackbarMessage = "Produit supprimé")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        form = state.form?.copy(
                            errorMessage = e.message ?: "Suppression impossible",
                        ),
                    )
                }
            }
        }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
