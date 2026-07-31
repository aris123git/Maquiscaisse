package com.maquis.caisse.ui.parametres

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.core.SettingsKeys
import com.maquis.caisse.data.backup.BackupManager
import com.maquis.caisse.data.print.EscPosPrinter
import com.maquis.caisse.domain.model.Permissions
import com.maquis.caisse.domain.repository.SettingsRepository
import com.maquis.caisse.domain.repository.UserRepository
import com.maquis.caisse.kiosk.KioskManager
import com.maquis.caisse.kiosk.KioskSecureStore
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
    val isAdmin: Boolean = false,
    val kioskEnabled: Boolean = false,
    val kioskAutoStart: Boolean = false,
    val kioskHasPin: Boolean = false,
    val kioskDeviceOwner: Boolean = false,
    val kioskLockedNow: Boolean = false,
)

data class BtDeviceUi(val name: String, val address: String)

@HiltViewModel
class ParametresViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val printer: EscPosPrinter,
    private val backupManager: BackupManager,
    private val userRepository: UserRepository,
    private val session: SessionManager,
    private val kioskManager: KioskManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(ParametresUiState())
    val ui: StateFlow<ParametresUiState> = _ui.asStateFlow()

    fun suggestedBackupName(): String = backupManager.suggestedFileName()

    fun isAdmin(): Boolean {
        val user = session.userOrNull() ?: return false
        return user.role == "ADMIN" || user.can(Permissions.MANAGE_USERS)
    }

    fun changeOwnPin(newPin: String, confirm: String) = viewModelScope.launch {
        if (newPin != confirm) {
            _ui.update { it.copy(message = "Les deux codes ne correspondent pas") }
            return@launch
        }
        val me = session.userOrNull()
        if (me == null) {
            _ui.update { it.copy(message = "Non connecté") }
            return@launch
        }
        try {
            userRepository.changePin(me.id, newPin)
            _ui.update { it.copy(message = "Ton code PIN a été modifié") }
        } catch (e: Exception) {
            _ui.update { it.copy(message = e.message ?: "Échec modification du code") }
        }
    }

    fun setKioskAdminPin(currentPin: String?, newPin: String, confirm: String): Boolean {
        if (!isAdmin()) {
            _ui.update { it.copy(message = "Réservé à l'administrateur") }
            return false
        }
        if (newPin != confirm) {
            _ui.update { it.copy(message = "Les deux codes administrateur ne correspondent pas") }
            return false
        }
        if (kioskManager.hasAdminPin()) {
            when (val result = kioskManager.verifyAdminPin(currentPin.orEmpty())) {
                is KioskSecureStore.PinVerifyResult.Ok -> Unit
                is KioskSecureStore.PinVerifyResult.Wrong -> {
                    _ui.update {
                        it.copy(message = "PIN administrateur incorrect (${result.remaining} essais restants)")
                    }
                    return false
                }
                is KioskSecureStore.PinVerifyResult.LockedOut -> {
                    _ui.update {
                        it.copy(message = "Trop d'essais — réessaie dans ${result.secondsRemaining}s")
                    }
                    return false
                }
                KioskSecureStore.PinVerifyResult.NoPinSet -> Unit
            }
        }
        return try {
            kioskManager.setAdminPin(newPin)
            refreshKioskUi()
            _ui.update { it.copy(message = "PIN administrateur kiosque enregistré") }
            true
        } catch (e: Exception) {
            _ui.update { it.copy(message = e.message ?: "Échec enregistrement PIN") }
            false
        }
    }

    fun setKioskEnabled(enabled: Boolean, adminPin: String, activity: Activity): Boolean {
        if (!isAdmin()) {
            _ui.update { it.copy(message = "Réservé à l'administrateur") }
            return false
        }
        if (!verifyPinForAdminAction(adminPin)) return false
        if (enabled && !kioskManager.hasAdminPin()) {
            _ui.update { it.copy(message = "Définis d'abord le PIN administrateur kiosque") }
            return false
        }
        if (enabled) {
            kioskManager.setEnabled(true)
            kioskManager.enterKiosk(activity)
            _ui.update { it.copy(message = "Mode kiosque activé (Lock Task)") }
        } else {
            kioskManager.disableKiosk(activity)
            _ui.update { it.copy(message = "Mode kiosque désactivé") }
        }
        refreshKioskUi()
        return true
    }

    fun setKioskAutoStart(enabled: Boolean, adminPin: String): Boolean {
        if (!isAdmin()) {
            _ui.update { it.copy(message = "Réservé à l'administrateur") }
            return false
        }
        if (!verifyPinForAdminAction(adminPin)) return false
        kioskManager.setAutoStart(enabled)
        refreshKioskUi()
        _ui.update {
            it.copy(
                message = if (enabled) {
                    "NexaPOS se lancera automatiquement au démarrage"
                } else {
                    "Démarrage automatique désactivé"
                },
            )
        }
        return true
    }

    fun exitKioskTemporary(adminPin: String, activity: Activity): Boolean {
        if (!isAdmin()) {
            _ui.update { it.copy(message = "Réservé à l'administrateur") }
            return false
        }
        if (!verifyPinForAdminAction(adminPin)) return false
        kioskManager.exitKiosk(activity)
        refreshKioskUi()
        _ui.update {
            it.copy(message = "Mode kiosque quitté temporairement (actif au prochain redémarrage)")
        }
        return true
    }

    /** Vérifie le PIN admin sans quitter le kiosque (étape avant confirmation). */
    fun checkAdminPin(adminPin: String): Boolean {
        if (!isAdmin()) {
            _ui.update { it.copy(message = "Réservé à l'administrateur") }
            return false
        }
        return verifyPinForAdminAction(adminPin)
    }

    private fun verifyPinForAdminAction(adminPin: String): Boolean {
        when (val result = kioskManager.verifyAdminPin(adminPin)) {
            KioskSecureStore.PinVerifyResult.Ok -> return true
            KioskSecureStore.PinVerifyResult.NoPinSet -> {
                _ui.update { it.copy(message = "Définis d'abord le PIN administrateur kiosque") }
                return false
            }
            is KioskSecureStore.PinVerifyResult.Wrong -> {
                _ui.update {
                    it.copy(message = "PIN administrateur incorrect (${result.remaining} essais restants)")
                }
                return false
            }
            is KioskSecureStore.PinVerifyResult.LockedOut -> {
                _ui.update {
                    it.copy(message = "Trop d'essais — réessaie dans ${result.secondsRemaining}s")
                }
                return false
            }
        }
    }

    private fun refreshKioskUi() {
        val ks = kioskManager.state.value
        _ui.update {
            it.copy(
                isAdmin = isAdmin(),
                kioskEnabled = ks.enabled,
                kioskAutoStart = ks.autoStart,
                kioskHasPin = ks.hasAdminPin,
                kioskDeviceOwner = kioskManager.isDeviceOwner(),
                kioskLockedNow = ks.shouldLockNow,
            )
        }
    }

    init {
        viewModelScope.launch { reload() }
        viewModelScope.launch {
            kioskManager.state.collect { refreshKioskUi() }
        }
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
        refreshKioskUi()
    }

    fun deviceOwnerHint(): String = kioskManager.deviceOwnerSetupHint()

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

private enum class KioskPinAction {
    SET_PIN,
    TOGGLE_ENABLED,
    TOGGLE_AUTO_START,
    EXIT_KIOSK,
}

@Composable
fun ParametresScreen(viewModel: ParametresViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity
    var permissionsReady by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var changeOwnPin by remember { mutableStateOf(false) }
    var kioskPinAction by remember { mutableStateOf<KioskPinAction?>(null) }
    var pendingKioskEnable by remember { mutableStateOf<Boolean?>(null) }
    var pendingAutoStart by remember { mutableStateOf<Boolean?>(null) }
    var confirmExitKiosk by remember { mutableStateOf(false) }
    var verifiedExitPin by remember { mutableStateOf<String?>(null) }

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
        OutlinedButton(
            onClick = { changeOwnPin = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Changer mon code PIN") }
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

        if (ui.isAdmin) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("MODE KIOSQUE", style = MaterialTheme.typography.titleLarge)
            Text(
                "Verrouille la tablette sur NexaPOS (Lock Task Mode Android). " +
                    "Le PIN administrateur kiosque est distinct des codes caissier.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = ui.kioskEnabled,
                    onCheckedChange = { checked ->
                        pendingKioskEnable = checked
                        kioskPinAction = KioskPinAction.TOGGLE_ENABLED
                    },
                )
                Text("Activer le mode kiosque")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = ui.kioskAutoStart,
                    onCheckedChange = { checked ->
                        pendingAutoStart = checked
                        kioskPinAction = KioskPinAction.TOGGLE_AUTO_START
                    },
                )
                Text("Lancer automatiquement NexaPOS au démarrage")
            }
            Text(
                "PIN administrateur : ${if (ui.kioskHasPin) "••••" else "non défini"}",
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedButton(
                onClick = { kioskPinAction = KioskPinAction.SET_PIN },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(if (ui.kioskHasPin) "Modifier le PIN administrateur" else "Définir le PIN administrateur")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Administration", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(
                onClick = { kioskPinAction = KioskPinAction.EXIT_KIOSK },
                enabled = ui.kioskEnabled && ui.kioskLockedNow,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Quitter le mode kiosque") }
            Text(
                if (ui.kioskDeviceOwner) {
                    "Device Owner actif — verrouillage système renforcé."
                } else {
                    viewModel.deviceOwnerHint()
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = { viewModel.save() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 8.dp),
        ) { Text("Enregistrer") }
        ui.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }

    if (changeOwnPin) {
        var pin by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { changeOwnPin = false },
            title = { Text("Changer mon code PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("Nouveau code") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("Confirmer") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.changeOwnPin(pin, confirm)
                        changeOwnPin = false
                    },
                    enabled = pin.length >= 4 && confirm.length >= 4,
                ) { Text("Valider") }
            },
            dismissButton = {
                TextButton(onClick = { changeOwnPin = false }) { Text("Annuler") }
            },
        )
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

    val pinAction = kioskPinAction
    if (pinAction != null) {
        KioskAdminPinDialog(
            action = pinAction,
            hasExistingPin = ui.kioskHasPin,
            onDismiss = {
                kioskPinAction = null
                pendingKioskEnable = null
                pendingAutoStart = null
            },
            onConfirm = { currentPin, newPin, confirmPin ->
                when (pinAction) {
                    KioskPinAction.SET_PIN -> {
                        if (viewModel.setKioskAdminPin(
                                currentPin,
                                newPin.orEmpty(),
                                confirmPin.orEmpty(),
                            )
                        ) {
                            kioskPinAction = null
                        }
                    }
                    KioskPinAction.TOGGLE_ENABLED -> {
                        val target = pendingKioskEnable ?: return@KioskAdminPinDialog
                        if (viewModel.setKioskEnabled(target, currentPin.orEmpty(), activity)) {
                            kioskPinAction = null
                            pendingKioskEnable = null
                        }
                    }
                    KioskPinAction.TOGGLE_AUTO_START -> {
                        val target = pendingAutoStart ?: return@KioskAdminPinDialog
                        if (viewModel.setKioskAutoStart(target, currentPin.orEmpty())) {
                            kioskPinAction = null
                            pendingAutoStart = null
                        }
                    }
                    KioskPinAction.EXIT_KIOSK -> {
                        if (viewModel.checkAdminPin(currentPin.orEmpty())) {
                            verifiedExitPin = currentPin
                            kioskPinAction = null
                            confirmExitKiosk = true
                        }
                    }
                }
            },
        )
    }

    if (confirmExitKiosk) {
        AlertDialog(
            onDismissRequest = {
                confirmExitKiosk = false
                verifiedExitPin = null
            },
            title = { Text("Quitter le mode kiosque ?") },
            text = {
                Text(
                    "La navigation Android sera temporairement débloquée. " +
                        "Le mode kiosque se réactivera après un redémarrage si toujours activé.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pin = verifiedExitPin.orEmpty()
                        confirmExitKiosk = false
                        verifiedExitPin = null
                        viewModel.exitKioskTemporary(pin, activity)
                    },
                ) { Text("Confirmer") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmExitKiosk = false
                        verifiedExitPin = null
                    },
                ) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun KioskAdminPinDialog(
    action: KioskPinAction,
    hasExistingPin: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (currentPin: String?, newPin: String?, confirmPin: String?) -> Unit,
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    val title = when (action) {
        KioskPinAction.SET_PIN ->
            if (hasExistingPin) "Modifier le PIN administrateur" else "PIN administrateur kiosque"
        KioskPinAction.EXIT_KIOSK -> "Code administrateur requis"
        else -> "Code administrateur requis"
    }

    val canSubmit = when (action) {
        KioskPinAction.SET_PIN -> {
            val newOk = newPin.length >= KioskSecureStore.MIN_PIN_LENGTH && newPin == confirmPin
            if (hasExistingPin) currentPin.length >= KioskSecureStore.MIN_PIN_LENGTH && newOk else newOk
        }
        else -> currentPin.length >= KioskSecureStore.MIN_PIN_LENGTH
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (action == KioskPinAction.EXIT_KIOSK || action != KioskPinAction.SET_PIN) {
                    Text(
                        text = "•".repeat(currentPin.length.coerceAtLeast(0)).ifEmpty { "••••" },
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (action != KioskPinAction.SET_PIN || hasExistingPin) {
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = {
                            currentPin = it.filter { c -> c.isDigit() }
                                .take(KioskSecureStore.MAX_PIN_LENGTH)
                        },
                        label = {
                            Text(
                                if (action == KioskPinAction.SET_PIN) {
                                    "PIN actuel"
                                } else {
                                    "Code administrateur"
                                },
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (action == KioskPinAction.SET_PIN) {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = {
                            newPin = it.filter { c -> c.isDigit() }
                                .take(KioskSecureStore.MAX_PIN_LENGTH)
                        },
                        label = { Text("Nouveau PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = {
                            confirmPin = it.filter { c -> c.isDigit() }
                                .take(KioskSecureStore.MAX_PIN_LENGTH)
                        },
                        label = { Text("Confirmer") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (action) {
                        KioskPinAction.SET_PIN -> onConfirm(
                            currentPin.takeIf { hasExistingPin },
                            newPin,
                            confirmPin,
                        )
                        else -> onConfirm(currentPin, null, null)
                    }
                },
                enabled = canSubmit,
            ) { Text("Valider") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
