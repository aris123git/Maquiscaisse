package com.maquis.caisse.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.model.Permissions
import com.maquis.caisse.domain.repository.UserRepository
import com.maquis.caisse.ui.common.DropdownField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class UserRoleOption(val role: String, val label: String, val isWaitress: Boolean) {
    ADMIN("ADMIN", "Administrateur", false),
    CAISSIER("CAISSIER", "Caissier", false),
    SERVEUSE("SERVEUSE", "Serveuse", true),
}

@HiltViewModel
class UsersViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val session: SessionManager,
) : ViewModel() {
    val users: StateFlow<List<AppUser>> = userRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val currentUser: StateFlow<AppUser?> = session.currentUser

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun canManage(): Boolean = session.can(Permissions.MANAGE_USERS)

    fun add(name: String, pin: String, roleOption: UserRoleOption) = viewModelScope.launch {
        if (!session.can(Permissions.MANAGE_USERS)) {
            _message.value = "Seul un administrateur peut créer un compte"
            return@launch
        }
        try {
            val permissions = when (roleOption) {
                UserRoleOption.ADMIN -> Permissions.ADMIN_DEFAULT.toSet()
                UserRoleOption.CAISSIER -> Permissions.CAISSIER_DEFAULT.toSet()
                UserRoleOption.SERVEUSE -> Permissions.SERVEUSE_DEFAULT.toSet()
            }
            userRepository.add(
                AppUser(
                    name = name.trim(),
                    pin = pin,
                    role = roleOption.role,
                    permissions = permissions,
                    isWaitress = roleOption.isWaitress,
                ),
            )
            _message.value = "Compte « ${name.trim()} » créé"
        } catch (e: Exception) {
            _message.value = e.message ?: "Échec création"
        }
    }

    fun delete(user: AppUser) = viewModelScope.launch {
        if (!session.can(Permissions.MANAGE_USERS)) {
            _message.value = "Permission insuffisante"
            return@launch
        }
        val current = session.userOrNull()
        if (current?.id == user.id) {
            _message.value = "Impossible de supprimer le compte actuellement connecté"
            return@launch
        }
        try {
            userRepository.deactivate(user.id)
            _message.value = "Compte « ${user.name} » supprimé"
        } catch (e: Exception) {
            _message.value = e.message ?: "Échec suppression"
        }
    }

    fun switchUser(user: AppUser) {
        session.setUser(user)
        _message.update { "Session : ${user.name}" }
    }

    fun logout() {
        session.logout()
    }

    fun consumeMessage() {
        _message.value = null
    }
}

@Composable
fun UsersScreen(viewModel: UsersViewModel = hiltViewModel()) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val current by viewModel.currentUser.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("1234") }
    var role by remember { mutableStateOf(UserRoleOption.SERVEUSE) }
    var toDelete by remember { mutableStateOf<AppUser?>(null) }
    val canManage = viewModel.canManage()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Utilisateurs & permissions", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Session : ${current?.name ?: "—"} (${current?.role ?: "—"})",
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Les comptes sont créés par l'administrateur. Chaque personne se connecte avec son nom + PIN.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (canManage) {
            Text("Créer un compte", style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("PIN") },
                    modifier = Modifier.weight(0.6f),
                    singleLine = true,
                )
            }
            DropdownField(
                label = "Rôle",
                selected = role,
                options = UserRoleOption.entries,
                optionLabel = { it.label },
                onSelect = { if (it != null) role = it },
            )
            Button(
                onClick = {
                    viewModel.add(name, pin, role)
                    name = ""
                    pin = "1234"
                },
                enabled = name.isNotBlank() && pin.length >= 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) { Text("Créer le compte") }
        } else {
            Text(
                "Demande à un administrateur de créer ou supprimer un compte.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }

        HorizontalDivider()
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(users, key = { it.id }) { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${user.name} · ${user.role}" +
                                if (user.isWaitress) " · serveuse" else "",
                        )
                        Text(
                            user.permissions.take(5).joinToString(", "),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { viewModel.switchUser(user) }) {
                        Text(if (user.id == current?.id) "Actif" else "Utiliser")
                    }
                    if (canManage && user.id != current?.id) {
                        TextButton(onClick = { toDelete = user }) {
                            Text("Supprimer", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                HorizontalDivider()
            }
        }

        OutlinedButton(
            onClick = { viewModel.logout() },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) { Text("Se déconnecter") }
    }

    toDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer ${user.name} ?") },
            text = {
                Text("Le compte sera désactivé et ne pourra plus se connecter. L'historique des ventes reste intact.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(user)
                        toDelete = null
                    },
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Annuler") }
            },
        )
    }
}
