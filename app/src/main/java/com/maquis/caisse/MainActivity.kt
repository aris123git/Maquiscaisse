package com.maquis.caisse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.maquis.caisse.data.local.DatabaseSeed
import com.maquis.caisse.navigation.MaquisNavGraph
import com.maquis.caisse.ui.theme.MaquisCaisseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var databaseSeed: DatabaseSeed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LaunchedEffect(Unit) {
                databaseSeed.ensureDefaults()
            }
            MaquisCaisseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MaquisNavGraph()
                }
            }
        }
    }
}
