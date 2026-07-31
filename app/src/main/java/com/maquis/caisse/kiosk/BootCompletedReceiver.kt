package com.maquis.caisse.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.maquis.caisse.MainActivity

/**
 * Relance NexaPOS après démarrage de la tablette (sans boucle ni service permanent).
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val store = KioskSecureStore(context.applicationContext)
        store.clearTemporarilyUnlockedOnBoot()

        if (!store.autoStart && !store.enabled) {
            Log.i(TAG, "Auto-start kiosque désactivé — ignoré")
            return
        }

        Log.i(TAG, "Démarrage auto NexaPOS après boot")
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            putExtra(EXTRA_FROM_BOOT, true)
        }
        context.startActivity(launch)
    }

    companion object {
        private const val TAG = "NexaKioskBoot"
        const val EXTRA_FROM_BOOT = "kiosk_from_boot"
    }
}
