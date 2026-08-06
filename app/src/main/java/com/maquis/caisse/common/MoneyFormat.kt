package com.maquis.caisse.common

import com.maquis.caisse.core.Constants
import java.text.NumberFormat
import java.util.Locale

/** Formatage des montants FCFA pour l'affichage (ex: `1 500 FCFA`). */
object MoneyFormat {
    private val numberFormat: NumberFormat =
        NumberFormat.getIntegerInstance(Locale.FRANCE)

    fun format(amount: Long): String =
        "${numberFormat.format(amount)} ${Constants.CURRENCY_LABEL}"

    // Normalise les espaces non-breakable et fullwidth digits avant envoi vers les imprimantes.
    fun forPrinter(amount: Long): String {
        val raw = numberFormat.format(amount)
        // NBSP -> ASCII space
        val normalized = raw.replace('\u00A0', ' ')
            // fullwidth digits -> ASCII
            .map { c ->
                if (c in '\uFF10'..'\uFF19') {
                    ('0' + (c - '\uFF10')).toChar()
                } else c
            }.joinToString("")
        return "$normalized ${Constants.CURRENCY_LABEL}"
    }
}
