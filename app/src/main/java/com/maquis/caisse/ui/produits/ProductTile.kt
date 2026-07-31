package com.maquis.caisse.ui.produits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.Product
import java.io.File

/**
 * Tuile produit pour la grille catalogue : photo (Coil) + nom + prix.
 * Fallback icône générique si pas d'image.
 */
@Composable
fun ProductTile(
    product: Product,
    imageFile: File?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (imageFile != null) {
                AsyncImage(
                    model = imageFile,
                    contentDescription = product.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!product.isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Inactif",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        Text(
            text = product.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = MoneyFormat.format(product.salePrice),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (product.category.isNotBlank()) {
            Text(
                text = product.category,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
