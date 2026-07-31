package com.maquis.caisse

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.maquis.caisse.data.local.DatabaseSeed
import com.maquis.caisse.navigation.MaquisNavGraph
import com.maquis.caisse.ui.login.LoginScreen
import com.maquis.caisse.ui.theme.MaquisCaisseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var databaseSeed: DatabaseSeed
    @Inject lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var ready by remember { mutableStateOf(false) }
            val currentUser by sessionManager.currentUser.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                databaseSeed.ensureDefaults()
                ready = true
            }

            MaquisCaisseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        !ready -> Unit
                        currentUser == null -> LoginScreen(
                            onLoggedIn = { /* session déjà remplie */ },
                        )
                        else -> MaquisNavGraph()
                    }
                }
            }
        }
    }
}
