package com.maquis.caisse.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.util.Log
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
 * Sauvegarde / restauration hors sandbox pour survivre à une mise à jour / désinstallation.
 * Contenu : base Room (+ WAL) et images produits, dans un ZIP (ou fichier .db brut).
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) {
    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.FRANCE).format(Date())
        return "nexages_backup_$stamp.zip"
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
            require(dbFile.length() > 100L) { "Base de données vide ou corrompue" }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(BufferedOutputStream(out)).use { zip ->
                    putFile(zip, dbFile, "db/$DB_NAME")
                    // Après checkpoint FULL, on n'embarque pas le WAL (évite les incohérences).
                    val imagesDir = File(context.filesDir, ProductImageStore.RELATIVE_DIR)
                    if (imagesDir.isDirectory) {
                        imagesDir.walkTopDown().filter { it.isFile }.forEach { file ->
                            val relative = file.relativeTo(imagesDir).path.replace('\\', '/')
                            putFile(zip, file, "images/$relative")
                        }
                    }
                    zip.putNextEntry(ZipEntry("meta.txt"))
                    zip.write(
                        buildString {
                            appendLine("maquis_caisse_backup")
                            appendLine("version=2")
                            appendLine("db=$DB_NAME")
                            appendLine("created=${System.currentTimeMillis()}")
                            appendLine("app=${context.packageName}")
                        }.toByteArray(Charsets.UTF_8),
                    )
                    zip.closeEntry()
                }
            } ?: error("Impossible d'écrire le fichier de sauvegarde")
            Unit
        }.onFailure { Log.e(TAG, "Export échoué", it) }
            .map { }
    }

    /**
     * Restaure depuis [uri] (ZIP NexaGes / Maquis, ou fichier .db brut), puis redémarre.
     */
    suspend fun importFromUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val staging = File(context.cacheDir, "restore_${System.currentTimeMillis()}").also {
                it.mkdirs()
            }
            try {
                val displayName = uri.lastPathSegment.orEmpty().lowercase(Locale.ROOT)
                val looksLikeDb = displayName.endsWith(".db") || displayName.contains(DB_NAME)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    if (looksLikeDb && !displayName.endsWith(".zip")) {
                        // Fichier .db brut (export manuel / copie).
                        val raw = File(staging, "db/$DB_NAME")
                        raw.parentFile?.mkdirs()
                        FileOutputStream(raw).use { out -> input.copyTo(out) }
                    } else {
                        unzip(input, staging)
                    }
                } ?: error("Impossible de lire la sauvegarde (fichier inaccessible)")

                val dbSrc = findDatabaseFile(staging)
                    ?: error(
                        "Sauvegarde invalide : base « $DB_NAME » introuvable dans l'archive. " +
                            "Choisis un fichier .zip exporté depuis Paramètres → Exporter, " +
                            "ou un fichier $DB_NAME.",
                    )
                validateSqliteFile(dbSrc)

                val walSrc = findSideFile(staging, dbSrc, "-wal")
                val shmSrc = findSideFile(staging, dbSrc, "-shm")

                // Ne ferme Room qu'après validation — sinon l'app reste cassée si le ZIP est mauvais.
                try {
                    checkpointWal()
                } catch (e: Exception) {
                    Log.w(TAG, "Checkpoint avant restore ignoré: ${e.message}")
                }
                database.close()

                val dbPath = context.getDatabasePath(DB_NAME)
                val dbDir = dbPath.parentFile ?: error("Répertoire base introuvable")
                dbDir.mkdirs()

                // Supprime l'ancienne base + journaux (évite mélange WAL / nouvelle base).
                deleteQuietly(File(dbDir, DB_NAME))
                deleteQuietly(File(dbDir, "$DB_NAME-wal"))
                deleteQuietly(File(dbDir, "$DB_NAME-shm"))
                deleteQuietly(File(dbDir, "$DB_NAME-journal"))

                copyAtomic(dbSrc, File(dbDir, DB_NAME))

                // Ne restaure le WAL que s'il est cohérent avec la base (même dossier d'origine).
                if (walSrc != null && walSrc.length() > 0L) {
                    copyAtomic(walSrc, File(dbDir, "$DB_NAME-wal"))
                    if (shmSrc != null && shmSrc.length() > 0L) {
                        copyAtomic(shmSrc, File(dbDir, "$DB_NAME-shm"))
                    }
                }

                val imagesStaging = findImagesDir(staging)
                val imagesDir = File(context.filesDir, ProductImageStore.RELATIVE_DIR)
                if (imagesStaging != null && imagesStaging.isDirectory) {
                    if (imagesDir.exists()) imagesDir.deleteRecursively()
                    imagesDir.mkdirs()
                    imagesStaging.copyRecursively(imagesDir, overwrite = true)
                }

                Log.i(TAG, "Restauration OK (${dbSrc.length()} octets)")
                Unit
            } finally {
                staging.deleteRecursively()
            }
        }.onFailure { Log.e(TAG, "Restauration échouée", it) }
            .map { }
    }

    fun restartApp() {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (intent != null) {
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Relance après restore échouée", e)
        }
        // Tue le process pour forcer une réouverture propre de Room.
        Process.killProcess(Process.myPid())
        Runtime.getRuntime().exit(0)
    }

    private suspend fun checkpointWal() {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
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
            var count = 0
            while (entry != null) {
                val name = normalizeZipPath(entry.name)
                if (name.isBlank() || name.startsWith("__MACOSX/") || name.endsWith(".DS_Store")) {
                    zip.closeEntry()
                    entry = zip.nextEntry
                    continue
                }
                val outFile = File(destDir, name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    val canonicalDest = destDir.canonicalFile
                    val canonicalOut = outFile.canonicalFile
                    require(canonicalOut.path.startsWith(canonicalDest.path + File.separator)) {
                        "Entrée ZIP invalide : ${entry.name}"
                    }
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out -> zip.copyTo(out) }
                    count++
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            require(count > 0) { "Archive ZIP vide ou illisible" }
        }
    }

    private fun normalizeZipPath(raw: String): String =
        raw.replace('\\', '/')
            .trimStart('/')
            .removePrefix("./")

    /**
     * Accepte `db/maquis_caisse.db`, un dossier racine encapsulé, ou un .db à la racine.
     */
    private fun findDatabaseFile(staging: File): File? {
        val preferred = File(staging, "db/$DB_NAME")
        if (preferred.isFile && preferred.length() > 0L) return preferred

        val matches = staging.walkTopDown()
            .filter { it.isFile }
            .filter { file ->
                val n = file.name.lowercase(Locale.ROOT)
                n == DB_NAME.lowercase(Locale.ROOT) ||
                    n == "maquis_caisse.db" ||
                    n == "nexages.db" ||
                    (n.endsWith(".db") && !n.contains("-wal") && !n.contains("-shm"))
            }
            .filter { it.length() > 100L }
            .toList()

        matches.firstOrNull { it.name.equals(DB_NAME, ignoreCase = true) }?.let { return it }
        matches.firstOrNull { it.name.equals("maquis_caisse.db", ignoreCase = true) }?.let { return it }
        // Un seul .db dans l'archive → on le prend.
        if (matches.size == 1) return matches.first()
        return matches.maxByOrNull { it.length() }
    }

    private fun findSideFile(staging: File, dbFile: File, suffix: String): File? {
        val sibling = File(dbFile.parentFile, dbFile.name + suffix)
        if (sibling.isFile) return sibling
        val named = File(staging, "db/$DB_NAME$suffix")
        if (named.isFile) return named
        return staging.walkTopDown()
            .firstOrNull { it.isFile && it.name.equals(DB_NAME + suffix, ignoreCase = true) }
    }

    private fun findImagesDir(staging: File): File? {
        val direct = File(staging, "images")
        if (direct.isDirectory) return direct
        return staging.walkTopDown()
            .firstOrNull { it.isDirectory && it.name.equals("images", ignoreCase = true) }
    }

    private fun validateSqliteFile(file: File) {
        require(file.isFile && file.length() > 100L) {
            "Fichier base trop petit ou manquant (${file.length()} octets)"
        }
        FileInputStream(file).use { input ->
            val header = ByteArray(16)
            val read = input.read(header)
            require(read == 16) { "Impossible de lire l'en-tête SQLite" }
            val magic = header.decodeToString()
            require(magic.startsWith("SQLite format 3")) {
                "Le fichier n'est pas une base SQLite valide (mauvais fichier sélectionné ?)"
            }
        }
    }

    private fun copyAtomic(src: File, dest: File) {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        deleteQuietly(tmp)
        src.copyTo(tmp, overwrite = true)
        FileOutputStream(tmp, true).use { it.fd.sync() }
        if (dest.exists()) deleteQuietly(dest)
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            deleteQuietly(tmp)
            FileOutputStream(dest, true).use { it.fd.sync() }
        }
        require(dest.exists() && dest.length() == src.length()) {
            "Échec copie base (${dest.length()} ≠ ${src.length()})"
        }
    }

    private fun deleteQuietly(file: File) {
        runCatching { if (file.exists()) file.delete() }
    }

    companion object {
        private const val TAG = "NexaBackup"
        const val DB_NAME = "maquis_caisse.db"
        const val MIME_ZIP = "application/zip"
    }
}
