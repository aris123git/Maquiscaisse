package com.maquis.caisse.ui.produits

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.maquis.caisse.core.Constants
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormScreen(
    form: ProductFormState,
    existingImageFile: File?,
    onBack: () -> Unit,
    onUpdate: ((ProductFormState) -> ProductFormState) -> Unit,
    onImagePicked: (Uri) -> Unit,
    onClearImage: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) onImagePicked(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = cameraUri
        if (success && uri != null) onImagePicked(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val uri = createCameraUri(context)
            cameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val uri = createCameraUri(context)
            cameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val isEdit = form.editingId != null
    val previewModel: Any? = form.pendingImageUri
        ?: existingImageFile?.takeIf { !form.clearExistingImage }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isEdit) "Modifier le produit" else "Nouveau produit")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (isEdit) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Supprimer",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (previewModel != null) {
                    AsyncImage(
                        model = previewModel,
                        contentDescription = "Photo produit",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Galerie")
                }
                FilledTonalButton(
                    onClick = { launchCamera() },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Photo")
                }
            }

            if (previewModel != null) {
                TextButton(onClick = onClearImage) {
                    Text("Retirer l'image")
                }
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = { value -> onUpdate { it.copy(name = value) } },
                label = { Text("Nom *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.category,
                onValueChange = { value -> onUpdate { it.copy(category = value) } },
                label = { Text("Catégorie") },
                singleLine = true,
                placeholder = { Text("Ex: Boissons, Plats…") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.salePrice,
                onValueChange = { value ->
                    onUpdate {
                        it.copy(
                            salePrice = value.filter { c -> c.isDigit() }
                                .take(Constants.MAX_MONEY_DIGITS),
                        )
                    }
                },
                label = { Text("Prix de vente (FCFA) *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.purchasePrice,
                onValueChange = { value ->
                    onUpdate {
                        it.copy(
                            purchasePrice = value.filter { c -> c.isDigit() }
                                .take(Constants.MAX_MONEY_DIGITS),
                        )
                    }
                },
                label = { Text("Prix d'achat (FCFA)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.stock,
                onValueChange = { value ->
                    onUpdate {
                        it.copy(
                            stock = value.filter { c -> c.isDigit() }
                                .take(Constants.MAX_STOCK_DIGITS),
                        )
                    }
                },
                label = { Text("Stock") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.alertThreshold,
                onValueChange = { value ->
                    onUpdate {
                        it.copy(
                            alertThreshold = value.filter { c -> c.isDigit() }
                                .take(Constants.MAX_STOCK_DIGITS),
                        )
                    }
                },
                label = { Text("Seuil d'alerte stock") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Produit actif", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = form.isActive,
                    onCheckedChange = { checked -> onUpdate { it.copy(isActive = checked) } },
                )
            }

            form.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Button(
                onClick = onSave,
                enabled = !form.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(if (form.isSaving) "Enregistrement…" else "Enregistrer")
            }
        }
    }
}

private fun createCameraUri(context: android.content.Context): Uri {
    val dir = File(context.cacheDir, "camera").also { it.mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}
