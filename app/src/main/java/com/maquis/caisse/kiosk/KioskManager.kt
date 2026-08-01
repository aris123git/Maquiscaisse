package com.maquis.caisse.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.maquis.caisse.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mode kiosque Android officiel : Lock Task Mode (+ Device Owner si provisionné).
 */
@Singleton
class KioskManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val store: KioskSecureStore,
) {
    private val dpm: DevicePolicyManager =
        appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent =
        ComponentName(appContext, NexaDeviceAdminReceiver::class.java)

    val state = store.state

    fun isEnabled(): Boolean = store.enabled

    fun isAutoStartEnabled(): Boolean = store.autoStart

    fun hasAdminPin(): Boolean = store.hasAdminPin()

    /** Verrouillage effectif (activé et pas de sortie admin temporaire). */
    fun shouldLockNow(): Boolean = store.enabled && !store.temporarilyUnlocked

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(appContext.packageName)

    fun isLockTaskActive(activity: Activity): Boolean {
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            @Suppress("DEPRECATION")
            am.isInLockTaskMode
        }
    }

    /**
     * Prépare le Device Owner (si applicable) puis entre en Lock Task Mode.
     */
    fun enterKiosk(activity: Activity) {
        if (!shouldLockNow()) return
        prepareDeviceOwnerPolicies()
        hideSystemUi(activity)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try {
            if (!isLockTaskActive(activity)) {
                activity.startLockTask()
            }
        } catch (_: Exception) {
            // Sans Device Owner / pinning, Android peut refuser — on garde le reste.
        }
    }

    fun exitKiosk(activity: Activity) {
        // D'abord lever le verrou logique pour empêcher onResume/LaunchedEffect de relancer Lock Task.
        store.temporarilyUnlocked = true
        try {
            if (isLockTaskActive(activity)) {
                activity.stopLockTask()
            }
        } catch (_: Exception) {
            try {
                activity.stopLockTask()
            } catch (_: Exception) {
                // ignore
            }
        }
        showSystemUi(activity)
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** Désactive durablement le mode kiosque (après confirmation admin). */
    fun disableKiosk(activity: Activity) {
        store.enabled = false
        store.temporarilyUnlocked = false
        try {
            if (isLockTaskActive(activity)) {
                activity.stopLockTask()
            }
        } catch (_: Exception) {
            // ignore
        }
        if (isDeviceOwner()) {
            try {
                dpm.setLockTaskPackages(adminComponent, emptyArray())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    dpm.setKeyguardDisabled(adminComponent, false)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dpm.setStatusBarDisabled(adminComponent, false)
                }
            } catch (_: Exception) {
                // ignore
            }
        }
        showSystemUi(activity)
    }

    fun setEnabled(enabled: Boolean) {
        store.enabled = enabled
        if (enabled) {
            // Kiosque activé ⇒ démarrage auto aussi (option persistante après reboot).
            store.autoStart = true
            store.temporarilyUnlocked = false
            prepareDeviceOwnerPolicies()
            setAsPreferredHomeIfDeviceOwner()
        } else {
            clearPreferredHomeIfDeviceOwner()
        }
    }

    fun setAutoStart(enabled: Boolean) {
        store.autoStart = enabled
    }

    fun setAdminPin(pin: String) = store.setAdminPin(pin)

    fun verifyAdminPin(pin: String): KioskSecureStore.PinVerifyResult =
        store.verifyAdminPin(pin)

    fun onBootCompleted() {
        // Après reboot : annuler une sortie temporaire et relancer le verrouillage.
        store.clearTemporarilyUnlockedOnBoot()
    }

    fun prepareDeviceOwnerPolicies() {
        if (!isDeviceOwner()) return
        try {
            dpm.setLockTaskPackages(adminComponent, arrayOf(appContext.packageName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(
                    adminComponent,
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE,
                )
                dpm.setStatusBarDisabled(adminComponent, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setKeyguardDisabled(adminComponent, true)
            }
            setAsPreferredHomeIfDeviceOwner()
        } catch (_: Exception) {
            // ignore
        }
    }

    /** Device Owner : NexaGes devient l'écran d'accueil → ouverture garantie au boot. */
    private fun setAsPreferredHomeIfDeviceOwner() {
        if (!isDeviceOwner()) return
        try {
            val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            val activity = ComponentName(appContext, MainActivity::class.java)
            dpm.addPersistentPreferredActivity(adminComponent, filter, activity)
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun clearPreferredHomeIfDeviceOwner() {
        if (!isDeviceOwner()) return
        try {
            dpm.clearPackagePersistentPreferredActivities(adminComponent, appContext.packageName)
        } catch (_: Exception) {
            // ignore
        }
    }

    fun hideSystemUi(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }

    private fun showSystemUi(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    fun deviceOwnerSetupHint(): String =
        "Pour un verrouillage total et un démarrage auto garanti, " +
            "provisionner Device Owner une fois via ADB :\n" +
            "adb shell dpm set-device-owner " +
            "${appContext.packageName}/.kiosk.NexaDeviceAdminReceiver\n" +
            "Sinon : Réglages Android → Applications par défaut → Application d'accueil → NexaGes."
}
