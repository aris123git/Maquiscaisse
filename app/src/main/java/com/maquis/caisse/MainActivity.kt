package com.maquis.caisse

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.core.activation.ActivationService
import com.maquis.caisse.data.local.DatabaseSeed
import com.maquis.caisse.kiosk.BootCompletedReceiver
import com.maquis.caisse.kiosk.KioskManager
import com.maquis.caisse.navigation.MaquisNavGraph
import com.maquis.caisse.ui.activation.ActivationScreen
import com.maquis.caisse.ui.login.LoginScreen
import com.maquis.caisse.ui.theme.MaquisCaisseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var databaseSeed: DatabaseSeed
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var kioskManager: KioskManager
    @Inject lateinit var activationService: ActivationService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleBootIntent(intent)
        enableEdgeToEdge()
        setContent {
            var ready by remember { mutableStateOf(false) }
            var activated by remember { mutableStateOf(activationService.isActivated()) }
            val currentUser by sessionManager.currentUser.collectAsStateWithLifecycle()
            val kioskState by kioskManager.state.collectAsStateWithLifecycle()
            val lockActive = kioskState.shouldLockNow

            LaunchedEffect(Unit) {
                databaseSeed.ensureDefaults()
                activated = activationService.isActivated()
                ready = true
            }

            LaunchedEffect(ready, lockActive, activated) {
                if (ready && activated && lockActive) {
                    kioskManager.enterKiosk(this@MainActivity)
                }
            }

            BackHandler(enabled = lockActive && activated) { /* bloqué */ }

            MaquisCaisseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        !ready -> Unit
                        !activated -> ActivationScreen(
                            activationService = activationService,
                            onActivated = { activated = true },
                            onQuit = { finishAffinity() },
                        )
                        currentUser == null -> LoginScreen(
                            onLoggedIn = { /* session déjà remplie */ },
                        )
                        else -> MaquisNavGraph()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleBootIntent(intent)
        tryEnterKiosk()
    }

    override fun onResume() {
        super.onResume()
        tryEnterKiosk()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus &&
            ::kioskManager.isInitialized &&
            ::activationService.isInitialized &&
            activationService.isActivated() &&
            kioskManager.shouldLockNow()
        ) {
            kioskManager.hideSystemUi(this)
        }
    }

    private fun handleBootIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(BootCompletedReceiver.EXTRA_FROM_BOOT, false) != true) return
        if (!::kioskManager.isInitialized) return
        // Au boot : l'option kiosque reste valable → on force le re-verrouillage.
        if (kioskManager.isEnabled()) {
            kioskManager.onBootCompleted()
        }
    }

    private fun tryEnterKiosk() {
        if (::kioskManager.isInitialized &&
            ::activationService.isInitialized &&
            activationService.isActivated() &&
            kioskManager.shouldLockNow()
        ) {
            kioskManager.enterKiosk(this)
        }
    }
}
