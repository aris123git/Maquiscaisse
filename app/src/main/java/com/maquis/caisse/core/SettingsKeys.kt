package com.maquis.caisse.core

object SettingsKeys {
    const val SHOP_NAME = "shop_name"
    const val SHOP_ADDRESS = "shop_address"
    const val SHOP_PHONE = "shop_phone"
    const val TICKET_FOOTER = "ticket_footer"
    const val PRINT_ENABLED = "print_enabled"
    const val PRINT_WIDTH = "print_width" // 58 | 80
    const val PRINTER_ADDRESS = "printer_address"
    const val PRINTER_NAME = "printer_name"
    // Page de codes ESC/POS envoyée à l'init (ESC t n).
    // "0"  = PC437 (défaut, imprimantes génériques occidentales)
    // "16" = WPC1252 (Windows-1252)
    // "-1" = aucune commande de page de codes (legacy / imprimantes sans ESC t)
    const val PRINTER_CODEPAGE = "printer_codepage"
    const val TABLES_ENABLED = "tables_enabled"
}
