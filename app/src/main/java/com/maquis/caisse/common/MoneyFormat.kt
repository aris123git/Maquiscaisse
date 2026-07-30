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
}
