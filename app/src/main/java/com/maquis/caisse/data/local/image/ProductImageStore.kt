package com.maquis.caisse.data.local.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stockage privé des images produit : copie depuis une Uri (galerie/caméra),
 * redimensionnement + compression JPEG (~200–300 Ko), chemin relatif persisté.
 */
@Singleton
class ProductImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val imagesDir: File
        get() = File(context.filesDir, RELATIVE_DIR).also { it.mkdirs() }

    /**
     * Compresse [sourceUri] et l'enregistre sous `product_images/…`.
     * @return chemin relatif à [Context.getFilesDir], ou null si échec.
     */
    suspend fun saveFromUri(sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(sourceUri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return@withContext null

        val sampleSize = calculateInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maxSide = MAX_SIDE_PX,
        )
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(sourceUri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return@withContext null

        val scaled = scaleToMaxSide(bitmap, MAX_SIDE_PX)
        if (scaled !== bitmap) bitmap.recycle()

        val jpegBytes = compressToTarget(scaled)
        scaled.recycle()

        val fileName = "${UUID.randomUUID()}.jpg"
        val outFile = File(imagesDir, fileName)
        outFile.writeBytes(jpegBytes)
        "$RELATIVE_DIR/$fileName"
    }

    /** Supprime une image existante (remplacement ou produit sans photo). */
    suspend fun deleteIfExists(relativePath: String?) = withContext(Dispatchers.IO) {
        if (relativePath.isNullOrBlank()) return@withContext
        val file = resolveFile(relativePath)
        if (file.exists()) file.delete()
    }

    /** Résout un chemin relatif en fichier absolu du stockage privé. */
    fun resolveFile(relativePath: String): File = File(context.filesDir, relativePath)

    private fun scaleToMaxSide(source: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxSide) return source
        val ratio = maxSide.toFloat() / longest
        val w = (source.width * ratio).toInt().coerceAtLeast(1)
        val h = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private fun compressToTarget(bitmap: Bitmap): ByteArray {
        var quality = 85
        var bytes: ByteArray
        do {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            bytes = stream.toByteArray()
            quality -= 10
        } while (bytes.size > TARGET_MAX_BYTES && quality >= MIN_QUALITY)
        return bytes
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxSide || h / 2 >= maxSide) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample.coerceAtLeast(1)
    }

    companion object {
        const val RELATIVE_DIR = "product_images"
        /** Côté max après décodage — assez pour une tuile caisse nette. */
        const val MAX_SIDE_PX = 1024
        /** Cible haute ~300 Ko. */
        const val TARGET_MAX_BYTES = 300 * 1024
        const val MIN_QUALITY = 45
    }
}
