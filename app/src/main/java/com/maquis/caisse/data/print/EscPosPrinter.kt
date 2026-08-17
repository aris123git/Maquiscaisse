package com.maquis.caisse.data.print

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.core.SettingsKeys
import com.maquis.caisse.domain.model.Order
import com.maquis.caisse.domain.model.OrderStatus
import com.maquis.caisse.domain.repository.SettingsRepository
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Impression thermique Bluetooth ESC/POS (optionnelle).
 *
 * Connexion durcie contre les échecs intermittents typiques des imprimantes
 * bon marché : [BluetoothAdapter.cancelDiscovery] avant chaque connect,
 * délai post-connexion avant écriture, retry avec backoff, et fallback
 * RFCOMM canal 1 (réflexion) si le SPP standard échoue.
 */
@Singleton
class EscPosPrinter @Inject constructor(
    private val settings: SettingsRepository,
) {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val POST_CONNECT_DELAY_MS = 250L
        const val RETRY_BASE_DELAY_MS = 400L
        const val PRE_CONNECT_SETTLE_MS = 150L
    }

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
        if (!settings.isPrintEnabled()) {
            return@withContext Result.failure(IllegalStateException("Impression désactivée"))
        }
        printRaw(buildOrderTicket(order))
    }

    private suspend fun printRaw(lines: List<String>): Result<Unit> {
        val address = settings.get(SettingsKeys.PRINTER_ADDRESS, "")
        if (address.isBlank()) {
            return Result.failure(
                IllegalStateException("Aucune imprimante sélectionnée dans Paramètres"),
            )
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return Result.failure(IllegalStateException("Bluetooth indisponible"))
        if (!adapter.isEnabled) {
            return Result.failure(IllegalStateException("Bluetooth désactivé"))
        }

        val device = adapter.getRemoteDevice(address)
        var lastError: Exception? = null

        for (attempt in 1..MAX_ATTEMPTS) {
            // Cause #1 des échecs RFCOMM Android : discovery encore active.
            runCatching { adapter.cancelDiscovery() }
            if (attempt == 1) delay(PRE_CONNECT_SETTLE_MS)

            val socket = runCatching { openSocket(adapter, device) }
                .onFailure { e -> lastError = e as? Exception ?: Exception(e) }
                .getOrNull()

            if (socket != null) {
                try {
                    // Beaucoup d'imprimantes thermiques ignorent les 1ers octets
                    // si on écrit immédiatement après connect().
                    delay(POST_CONNECT_DELAY_MS)
                    socket.outputStream.use { out ->
                        writeEscPos(out, lines)
                    }
                    return Result.success(Unit)
                } catch (e: Exception) {
                    lastError = e
                } finally {
                    runCatching { socket.close() }
                }
            }

            if (attempt < MAX_ATTEMPTS) {
                delay(RETRY_BASE_DELAY_MS * attempt)
            }
        }

        return Result.failure(
            IllegalStateException(
                lastError?.message?.takeIf { it.isNotBlank() }
                    ?: "Échec connexion imprimante après $MAX_ATTEMPTS essais",
                lastError,
            ),
        )
    }

    /**
     * Ordre des stratégies (chaque connect est précédé de cancelDiscovery) :
     * 1. SPP sécurisé (UUID standard)
     * 2. SPP insecure
     * 3. RFCOMM canal 1 via réflexion (hack imprimantes cheap / tablettes)
     */
    @SuppressLint("MissingPermission")
    private fun openSocket(
        adapter: BluetoothAdapter,
        device: BluetoothDevice,
    ): BluetoothSocket {
        val errors = mutableListOf<Throwable>()

        fun tryConnect(label: String, factory: () -> BluetoothSocket): BluetoothSocket? {
            runCatching { adapter.cancelDiscovery() }
            var socket: BluetoothSocket? = null
            return try {
                socket = factory()
                socket.connect()
                socket
            } catch (e: Exception) {
                errors += IllegalStateException("$label: ${e.message}", e)
                runCatching { socket?.close() }
                null
            }
        }

        tryConnect("SPP") {
            device.createRfcommSocketToServiceRecord(sppUuid)
        }?.let { return it }

        tryConnect("SPP insecure") {
            device.createInsecureRfcommSocketToServiceRecord(sppUuid)
        }?.let { return it }

        tryConnect("RFCOMM canal 1") {
            val method = device.javaClass.getMethod(
                "createRfcommSocket",
                Int::class.javaPrimitiveType,
            )
            @Suppress("UNCHECKED_CAST")
            method.invoke(device, 1) as BluetoothSocket
        }?.let { return it }

        throw errors.lastOrNull()
            ?: IllegalStateException("Impossible d'ouvrir un socket Bluetooth")
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
        val name = settings.get(SettingsKeys.SHOP_NAME, "NexaGes")
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
        val shop = settings.get(SettingsKeys.SHOP_NAME, "NexaGes")
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
