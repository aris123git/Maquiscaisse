package com.maquis.caisse.core

/**
 * Constantes transverses (formats de date, devise, limites d'affichage...).
 * Ce fichier grandira au fil des sprints — ne pas y mettre de logique
 * métier, seulement des valeurs de configuration.
 */
object Constants {
    const val CURRENCY_LABEL = "FCFA"
    const val MAX_QUANTITY_DIGITS = 4 // limite raisonnable de saisie au pavé numérique

    /** Nombre de colonnes de la grille produits (usage une main / tuiles larges). */
    const val PRODUCT_GRID_COLUMNS = 2
}
