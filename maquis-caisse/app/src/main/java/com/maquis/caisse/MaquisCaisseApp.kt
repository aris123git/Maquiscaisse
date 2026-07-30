package com.maquis.caisse

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Point d'entrée Hilt de l'application.
 * Toute la génération de graphe de dépendances (DatabaseModule, futurs
 * RepositoryModule, etc.) part de cette classe.
 */
@HiltAndroidApp
class MaquisCaisseApp : Application()
