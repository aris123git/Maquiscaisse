package com.maquis.caisse.core.activation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Activation hors-ligne liée à l'appareil.
 *
 * - Premier lancement : demande le code maître.
 * - Changement d'ANDROID_ID (nouvel appareil / réinstall) ou d'IMEI : redemande le code.
 *
 * Sur beaucoup de tablettes l'IMEI n'est pas accessible (Android 10+) :
 * on utilise alors ANDROID_ID, et on mémorise l'IMEI dès qu'il devient disponible.
 */
@Singleton
class ActivationService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun currentDeviceId(): String {
        val imei = readImei(context)
        val androidId = androidId(context)
        return if (!imei.isNullOrBlank()) "imei:$imei" else "aid:$androidId"
    }

    fun isActivated(): Boolean {
        val token = prefs.getString(KEY_TOKEN, null) ?: return false
        if (token != expectedToken()) return false

        val storedAid = prefs.getString(KEY_ANDROID_ID, null) ?: return false
        val currentAid = androidId(context)
        if (storedAid != currentAid) return false

        val storedImei = prefs.getString(KEY_IMEI, null)
        val currentImei = readImei(context)
        if (!storedImei.isNullOrBlank() && !currentImei.isNullOrBlank() && storedImei != currentImei) {
            return false
        }
        // Première fois qu'on lit un IMEI après activation → le mémoriser sans redemander.
        if (storedImei.isNullOrBlank() && !currentImei.isNullOrBlank()) {
            prefs.edit().putString(KEY_IMEI, currentImei).apply()
        }
        return true
    }

    /** true si un appareil était activé mais l'identifiant a changé. */
    fun isDeviceMismatch(): Boolean {
        val token = prefs.getString(KEY_TOKEN, null) ?: return false
        if (token != expectedToken()) return false
        if (prefs.getString(KEY_ANDROID_ID, null) == null) return false
        return !isActivated()
    }

    fun verifyCode(code: String): Boolean =
        normalize(code) == normalize(MASTER_KEY)

    fun activate(code: String): Boolean {
        if (!verifyCode(code)) return false
        prefs.edit()
            .putString(KEY_TOKEN, expectedToken())
            .putString(KEY_ANDROID_ID, androidId(context))
            .putString(KEY_IMEI, readImei(context))
            .putLong(KEY_ACTIVATED_AT, System.currentTimeMillis())
            .apply()
        return true
    }

    fun clearActivation() {
        prefs.edit().clear().apply()
    }

    private fun expectedToken(): String = sha256(normalize(MASTER_KEY))

    private fun normalize(code: String): String =
        code.filterNot { it.isWhitespace() }.uppercase()

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MASTER_KEY = "ARIS-2026-NEXA-5363"
        private const val PREFS_NAME = "nexages_activation"
        private const val KEY_TOKEN = "token"
        private const val KEY_ANDROID_ID = "android_id"
        private const val KEY_IMEI = "imei"
        private const val KEY_ACTIVATED_AT = "activated_at"

        @SuppressLint("HardwareIds")
        fun androidId(context: Context): String =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                .orEmpty()
                .ifBlank { "unknown" }

        @SuppressLint("HardwareIds", "MissingPermission")
        fun readImei(context: Context): String? {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return null
            return try {
                val tm = context.getSystemService(TelephonyManager::class.java) ?: return null
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        tm.imei?.takeIf { it.isNotBlank() }
                            ?: tm.meid?.takeIf { it.isNotBlank() }
                    }
                    else -> {
                        @Suppress("DEPRECATION")
                        tm.deviceId?.takeIf { it.isNotBlank() }
                    }
                }
            } catch (_: SecurityException) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }
}
