package com.maquis.caisse.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.withTransaction
import com.maquis.caisse.data.local.AppDatabase
import com.maquis.caisse.data.local.image.ProductImageStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sauvegarde / restauration hors sandbox pour survivre à une désinstallation.
 * Contenu : base Room (+ WAL) et images produits, dans un ZIP.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) {
    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.FRANCE).format(Date())
        return "maquis_caisse_backup_$stamp.zip"
    }

    /**
     * Écrit une archive ZIP vers [uri] (SAF CreateDocument).
     * Ne bloque pas la caisse : à appeler hors chemin de vente.
     */
    suspend fun exportToUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            checkpointWal()
            val dbFile = context.getDatabasePath(DB_NAME)
            require(dbFile.exists()) { "Base de données introuvable" }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(BufferedOutputStream(out)).use { zip ->
                    putFile(zip, dbFile, "db/$DB_NAME")
                    listOf("$DB_NAME-wal", "$DB_NAME-shm").forEach { name ->
                        val side = File(dbFile.parentFile, name)
                        if (side.exists() && side.length() > 0L) {
                            putFile(zip, side, "db/$name")
                        }
                    }
                    val imagesDir = File(context.filesDir, ProductImageStore.RELATIVE_DIR)
                    if (imagesDir.isDirectory) {
                        imagesDir.walkTopDown().filter { it.isFile }.forEach { file ->
                            val relative = file.relativeTo(imagesDir).path.replace('\\', '/')
                            putFile(zip, file, "images/$relative")
                        }
                    }
                    zip.putNextEntry(ZipEntry("meta.txt"))
                    zip.write(
                        "maquis_caisse_backup\nversion=1\ncreated=${System.currentTimeMillis()}\n"
                            .toByteArray(Charsets.UTF_8),
                    )
                    zip.closeEntry()
                }
            } ?: error("Impossible d'écrire le fichier de sauvegarde")
        }
    }

    /**
     * Restaure depuis [uri] puis redémarre l'application.
     */
    suspend fun importFromUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val staging = File(context.cacheDir, "restore_${System.currentTimeMillis()}").also {
                it.mkdirs()
            }
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    unzip(input, staging)
                } ?: error("Impossible de lire la sauvegarde")

                val dbSrc = File(staging, "db/$DB_NAME")
                require(dbSrc.exists()) { "Sauvegarde invalide (base manquante)" }

                // Ferme Room pour libérer les fichiers SQLite.
                database.close()

                val dbDir = context.getDatabasePath(DB_NAME).parentFile
                    ?: error("Répertoire base introuvable")
                dbDir.mkdirs()
                File(dbDir, DB_NAME).delete()
                File(dbDir, "$DB_NAME-wal").delete()
                File(dbDir, "$DB_NAME-shm").delete()

                dbSrc.copyTo(File(dbDir, DB_NAME), overwrite = true)
                listOf("$DB_NAME-wal", "$DB_NAME-shm").forEach { name ->
                    val src = File(staging, "db/$name")
                    if (src.exists()) {
                        src.copyTo(File(dbDir, name), overwrite = true)
                    }
                }

                val imagesStaging = File(staging, "images")
                val imagesDir = File(context.filesDir, ProductImageStore.RELATIVE_DIR)
                if (imagesStaging.isDirectory) {
                    if (imagesDir.exists()) imagesDir.deleteRecursively()
                    imagesDir.mkdirs()
                    imagesStaging.copyRecursively(imagesDir, overwrite = true)
                }
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    private suspend fun checkpointWal() {
        // Force le flush WAL dans le fichier principal avant copie.
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
        // Touch transaction to ensure Room is idle.
        database.withTransaction { }
    }

    private fun putFile(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }

    private fun unzip(input: java.io.InputStream, destDir: File) {
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    // Empêche path traversal
                    val canonicalDest = destDir.canonicalFile
                    val canonicalOut = outFile.canonicalFile
                    require(canonicalOut.path.startsWith(canonicalDest.path + File.separator)) {
                        "Entrée ZIP invalide"
                    }
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out -> zip.copyTo(out) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    companion object {
        const val DB_NAME = "maquis_caisse.db"
        const val MIME_ZIP = "application/zip"
    }
}
