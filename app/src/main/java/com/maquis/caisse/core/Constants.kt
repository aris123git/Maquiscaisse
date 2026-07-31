package com.maquis.caisse.core

/**
 * Constantes transverses (formats de date, devise, limites d'affichage...).
 * Ce fichier grandira au fil des sprints — ne pas y mettre de logique
 * métier, seulement des valeurs de configuration.
 */
object Constants {
    const val CURRENCY_LABEL = "FCFA"
    const val MAX_QUANTITY_DIGITS = 4 // limite raisonnable de saisie au pavé numérique
    const val MAX_MONEY_DIGITS = 9
    const val MAX_STOCK_DIGITS = 7

    /** Grille produits en paysage (comme catalogue Gestion_app). */
    const val PRODUCT_GRID_COLUMNS = 3
    const val CAISSE_GRID_COLUMNS = 3
}
