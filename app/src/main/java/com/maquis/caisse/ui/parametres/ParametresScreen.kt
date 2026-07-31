package com.maquis.caisse.ui.parametres

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.core.SettingsKeys
import com.maquis.caisse.data.backup.BackupManager
import com.maquis.caisse.data.print.EscPosPrinter
import com.maquis.caisse.domain.repository.SettingsRepository
import com.maquis.caisse.ui.common.DropdownField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ParametresUiState(
    val shopName: String = "",
    val shopAddress: String = "",
    val shopPhone: String = "",
    val ticketFooter: String = "Merci pour votre visite.",
    val printEnabled: Boolean = false,
    val printWidth: String = "58",
    val printerAddress: String = "",
    val printerName: String = "",
    val tablesEnabled: Boolean = true,
    val devices: List<BtDeviceUi> = emptyList(),
    val message: String? = null,
    val backupBusy: Boolean = false,
)

data class BtDeviceUi(val name: String, val address: String)

@HiltViewModel
class ParametresViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val printer: EscPosPrinter,
    private val backupManager: BackupManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(ParametresUiState())
    val ui: StateFlow<ParametresUiState> = _ui.asStateFlow()

    fun suggestedBackupName(): String = backupManager.suggestedFileName()

    init {
        viewModelScope.launch { reload() }
    }

    suspend fun reload() {
        _ui.update {
            it.copy(
                shopName = settings.get(SettingsKeys.SHOP_NAME, "Maquis Caisse"),
                shopAddress = settings.get(SettingsKeys.SHOP_ADDRESS, ""),
                shopPhone = settings.get(SettingsKeys.SHOP_PHONE, ""),
                ticketFooter = settings.get(SettingsKeys.TICKET_FOOTER, "Merci pour votre visite."),
                printEnabled = settings.isPrintEnabled(),
                printWidth = settings.get(SettingsKeys.PRINT_WIDTH, "58"),
                printerAddress = settings.get(SettingsKeys.PRINTER_ADDRESS, ""),
                printerName = settings.get(SettingsKeys.PRINTER_NAME, ""),
                tablesEnabled = settings.get(SettingsKeys.TABLES_ENABLED, "true") == "true",
            )
        }
    }

    fun update(transform: (ParametresUiState) -> ParametresUiState) {
        _ui.update(transform)
    }

    fun save() = viewModelScope.launch {
        val s = _ui.value
        settings.set(SettingsKeys.SHOP_NAME, s.shopName)
        settings.set(SettingsKeys.SHOP_ADDRESS, s.shopAddress)
        settings.set(SettingsKeys.SHOP_PHONE, s.shopPhone)
        settings.set(SettingsKeys.TICKET_FOOTER, s.ticketFooter.ifBlank { "Merci pour votre visite." })
        settings.setPrintEnabled(s.printEnabled)
        settings.set(SettingsKeys.PRINT_WIDTH, s.printWidth)
        settings.set(SettingsKeys.PRINTER_ADDRESS, s.printerAddress)
        settings.set(SettingsKeys.PRINTER_NAME, s.printerName)
        settings.set(SettingsKeys.TABLES_ENABLED, s.tablesEnabled.toString())
        _ui.update { it.copy(message = "Paramètres enregistrés") }
    }

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        _ui.update { it.copy(backupBusy = true, message = "Export en cours…") }
        val result = backupManager.exportToUri(uri)
        _ui.update {
            it.copy(
                backupBusy = false,
                message = if (result.isSuccess) {
                    "Sauvegarde exportée. Garde ce fichier avant toute désinstallation."
                } else {
                    result.exceptionOrNull()?.message ?: "Échec de l'export"
                },
            )
        }
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        _ui.update { it.copy(backupBusy = true, message = "Restauration… l'app va redémarrer") }
        val result = backupManager.importFromUri(uri)
        if (result.isSuccess) {
            backupManager.restartApp()
        } else {
            _ui.update {
                it.copy(
                    backupBusy = false,
                    message = result.exceptionOrNull()?.message ?: "Échec de la restauration",
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshBluetoothDevices() {
        val devices = printer.bondedDevices().map { d ->
            BtDeviceUi(name = d.name ?: "Imprimante", address = d.address)
        }
        _ui.update { it.copy(devices = devices, message = "${devices.size} appareil(s) appairé(s)") }
    }

    fun selectPrinter(device: BtDeviceUi) {
        _ui.update {
            it.copy(printerAddress = device.address, printerName = device.name)
        }
    }

    fun testPrint() = viewModelScope.launch {
        if (!_ui.value.printEnabled) {
            _ui.update { it.copy(message = "Impression désactivée — aucun envoi") }
            return@launch
        }
        val result = printer.testPrint()
        _ui.update {
            it.copy(
                message = if (result.isSuccess) {
                    "Test imprimé"
                } else {
                    result.exceptionOrNull()?.message ?: "Échec impression"
                },
            )
        }
    }
}

@Composable
fun ParametresScreen(viewModel: ParametresViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var permissionsReady by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupManager.MIME_ZIP),
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            confirmRestore = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissionsReady = true }

    LaunchedEffect(Unit) {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Paramètres", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(value = ui.shopName, onValueChange = { v -> viewModel.update { it.copy(shopName = v) } }, label = { Text("Nom du commerce") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = ui.shopAddress, onValueChange = { v -> viewModel.update { it.copy(shopAddress = v) } }, label = { Text("Adresse") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = ui.shopPhone, onValueChange = { v -> viewModel.update { it.copy(shopPhone = v) } }, label = { Text("Téléphone") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = ui.ticketFooter, onValueChange = { v -> viewModel.update { it.copy(ticketFooter = v) } }, label = { Text("Message ticket") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = ui.tablesEnabled,
                onCheckedChange = { c -> viewModel.update { it.copy(tablesEnabled = c) } },
            )
            Text("Activer la gestion des tables")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Sauvegarde des données", style = MaterialTheme.typography.titleLarge)
        Text(
            "Avant une désinstallation (conflit d'APK), exporte une sauvegarde. " +
                "Après réinstallation, restaure ce fichier. La caisse n'est pas ralentie.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { exportLauncher.launch(viewModel.suggestedBackupName()) },
            enabled = !ui.backupBusy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Exporter la sauvegarde") }
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf(BackupManager.MIME_ZIP, "application/octet-stream", "*/*")) },
            enabled = !ui.backupBusy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Restaurer une sauvegarde") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Impression", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = ui.printEnabled,
                onCheckedChange = { c -> viewModel.update { it.copy(printEnabled = c) } },
            )
            Text("Activer l'impression des tickets")
        }

        if (ui.printEnabled) {
            DropdownField(
                label = "Format",
                selected = ui.printWidth,
                options = listOf("58", "80"),
                optionLabel = { "$it mm" },
                onSelect = { v -> if (v != null) viewModel.update { it.copy(printWidth = v) } },
            )
            Text(
                "Imprimante : ${ui.printerName.ifBlank { "aucune" }} ${ui.printerAddress}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (permissionsReady || true) viewModel.refreshBluetoothDevices()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Rechercher Bluetooth") }
                OutlinedButton(
                    onClick = { viewModel.testPrint() },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Tester l'impression") }
            }
            ui.devices.forEach { device ->
                OutlinedButton(
                    onClick = { viewModel.selectPrinter(device) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Text("${device.name} (${device.address})")
                }
            }
        } else {
            Text(
                "Impression désactivée — aucun envoi ni message d'erreur imprimante.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = { viewModel.save() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 8.dp),
        ) { Text("Enregistrer") }
        ui.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = {
                confirmRestore = false
                pendingRestoreUri = null
            },
            title = { Text("Restaurer la sauvegarde ?") },
            text = {
                Text(
                    "Les données actuelles seront remplacées. L'application redémarrera ensuite.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingRestoreUri
                        confirmRestore = false
                        pendingRestoreUri = null
                        if (uri != null) viewModel.importBackup(uri)
                    },
                ) { Text("Restaurer") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        pendingRestoreUri = null
                    },
                ) { Text("Annuler") }
            },
        )
    }
}
