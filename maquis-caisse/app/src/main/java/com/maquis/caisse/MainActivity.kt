package com.maquis.caisse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.maquis.caisse.navigation.MaquisNavGraph
import com.maquis.caisse.ui.theme.MaquisCaisseTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity unique de l'application (architecture single-activity,
 * navigation gérée entièrement par Navigation Compose).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaquisCaisseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MaquisNavGraph()
                }
            }
        }
    }
}
