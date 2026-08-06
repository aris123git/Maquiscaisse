package com.maquis.caisse.data.print

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.maquis.caisse.core.SettingsKeys
import com.maquis.caisse.domain.model.Order
import com.maquis.caisse.domain.model.OrderStatus
import com.maquis.caisse.domain.repository.SettingsRepository
import com.maquis.caisse.common.MoneyFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Impression thermique Bluetooth ESC/POS (optionnelle).
 * Ne bloque jamais l'app si désactivée ou sans imprimante.
 */
@Singleton
class EscPosPrinter @Inject constructor(
    private val settings: SettingsRepository,
) {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val maxRetries = 3
    private val retryDelayMs = 500L
    private val connectTimeoutMs = 2000L

    suspend fun isEnabled(): Boolean = settings.isPrintEnabled()

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return adapter.bondedDevices?.toList().orEmpty()
    }

    suspend fun testPrint(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!settings.isPrintEnabled()) {
            return@withContext Result.failure(IllegalStateException("Impression désactivée"))
        }
        printRaw(buildTestTicket())
    }

    suspend fun printOrder(order: Order): Result<Unit> = withContext(Dispatchers.IO) {
        if (!settings.isPrintEnabled()) return@withContext Result.success(Unit)
        printRaw(buildOrderTicket(order))
    }

    private suspend fun printRaw(lines: List<String>): Result<Unit> {
        val address = settings.get(SettingsKeys.PRINTER_ADDRESS, "")
        if (address.isBlank()) {
            return Result.failure(IllegalStateException("Aucune imprimante sélectionnée"))
        }

        var lastException: Exception? = null

        // Retry loop avec délai progressif
        for (attempt in 1..maxRetries) {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                    ?: return Result.failure(IllegalStateException("Bluetooth indisponible"))

                val device = adapter.getRemoteDevice(address)
                val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(sppUuid)

                try {
                    adapter.cancelDiscovery()
                    
                    // Attendre un peu avant de connecter pour éviter les race conditions
                    delay(100)
                    
                    // Connecter avec timeout
                    socket.connect()

                    // Petit délai après connexion pour s'assurer que le socket est vraiment prêt
                    delay(200)

                    // Écrire les données
                    socket.outputStream.use { out ->
                        writeEscPos(out, lines)
                    }

                    // Flush supplémentaire pour s'assurer que tout est envoyé
                    delay(100)

                    socket.close()
                    return Result.success(Unit)

                } catch (e: Exception) {
                    lastException = e
                    try {
                        socket.close()
                    } catch (closeException: Exception) {
                        // Ignorer les erreurs de fermeture
                    }

                    // Si ce n'est pas la dernière tentative, attendre avant de réessayer
                    if (attempt < maxRetries) {
                        val delayTime = retryDelayMs * attempt
                        delay(delayTime)
                    }
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    val delayTime = retryDelayMs * attempt
                    delay(delayTime)
                }
            }
        }

        // Retourner la dernière exception après tous les essais
        return Result.failure(
            lastException ?: Exception("Impossible de connecter à l'imprimante après $maxRetries tentatives")
        )
    }

    private fun writeEscPos(out: OutputStream, lines: List<String>) {
        out.write(byteArrayOf(0x1B, 0x40)) // init
        lines.forEach { line ->
            out.write(line.toByteArray(Charsets.UTF_8))
            out.write(byteArrayOf(0x0A))
        }
        out.write(byteArrayOf(0x0A, 0x0A, 0x0A))
        out.write(byteArrayOf(0x1D, 0x56, 0x00)) // cut
        out.flush()
    }

    private suspend fun buildTestTicket(): List<String> {
        val width = settings.get(SettingsKeys.PRINT_WIDTH, "58").toIntOrNull() ?: 58
        val name = settings.get(SettingsKeys.SHOP_NAME, "Maquis Caisse")
        return listOf(
            center(name, width),
            center("TEST IMPRESSION", width),
            "----------------",
            "OK ${Date()}",
            "----------------",
        )
    }

    private suspend fun buildOrderTicket(order: Order): List<String> {
        val width = settings.get(SettingsKeys.PRINT_WIDTH, "58").toIntOrNull() ?: 58
        val shop = settings.get(SettingsKeys.SHOP_NAME, "Maquis Caisse")
        val address = settings.get(SettingsKeys.SHOP_ADDRESS, "")
        val phone = settings.get(SettingsKeys.SHOP_PHONE, "")
        val footer = settings.get(SettingsKeys.TICKET_FOOTER, "Merci pour votre visite.")
        val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
        val lines = mutableListOf<String>()
        lines += center(shop, width)
        if (address.isNotBlank()) lines += center(address, width)
        if (phone.isNotBlank()) lines += center(phone, width)
        lines += "----------------"
        lines += "Cmd: ${order.publicId}"
        lines += df.format(Date(order.createdAtEpochMs))
        order.waitressName?.let { lines += "Serveuse: $it" }
        order.tableLabel?.let { lines += "Table: $it" }
        lines += "----------------"
        order.items.forEach { item ->
            lines += item.productName
            lines += "  ${item.quantity} x ${MoneyFormat.format(item.unitPrice)}"
            lines += "  = ${MoneyFormat.format(item.lineTotal)}"
        }
        lines += "----------------"
        lines += "TOTAL: ${MoneyFormat.format(order.totalAmount)}"
        lines += "Statut: ${order.status.label}"
        if (order.payments.isNotEmpty()) {
            order.payments.forEach { p ->
                lines += "${p.paymentMode.label}: ${MoneyFormat.format(p.amount)}"
                if (p.amountTendered > 0) {
                    lines += "Recu: ${MoneyFormat.format(p.amountTendered)}"
                    lines += "Monnaie: ${MoneyFormat.format(p.changeAmount)}"
                }
            }
        } else if (order.status != OrderStatus.PAYEE) {
            lines += "NON PAYE"
        }
        lines += "----------------"
        lines += center(footer, width)
        return lines
    }

    private fun center(text: String, widthMm: Int): String {
        val cols = if (widthMm >= 80) 48 else 32
        if (text.length >= cols) return text.take(cols)
        val pad = (cols - text.length) / 2
        return " ".repeat(pad) + text
    }
}
