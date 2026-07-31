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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UsersViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val session: SessionManager,
) : ViewModel() {
    val users: StateFlow<List<AppUser>> = userRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val currentUser: StateFlow<AppUser> = session.currentUser

    fun add(name: String, pin: String, isWaitress: Boolean) = viewModelScope.launch {
        if (!session.can(Permissions.MANAGE_USERS)) return@launch
        userRepository.add(
            AppUser(
                name = name.trim(),
                pin = pin.ifBlank { "0000" },
                role = if (isWaitress) "SERVEUSE" else "CAISSIER",
                permissions = if (isWaitress) Permissions.SERVEUSE_DEFAULT.toSet() else Permissions.CAISSIER_DEFAULT.toSet(),
                isWaitress = isWaitress,
            ),
        )
    }

    fun switchUser(user: AppUser) {
        session.setUser(user)
    }
}

@Composable
fun UsersScreen(viewModel: UsersViewModel = hiltViewModel()) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val current by viewModel.currentUser.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("1234") }
    var isWaitress by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Utilisateurs & permissions", style = MaterialTheme.typography.headlineMedium)
        Text("Session : ${current.name} (${current.role})", color = MaterialTheme.colorScheme.primary)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("PIN") }, modifier = Modifier.weight(0.6f), singleLine = true)
            FilterChip(selected = isWaitress, onClick = { isWaitress = !isWaitress }, label = { Text(if (isWaitress) "Serveuse" else "Caissier") })
            Button(
                onClick = {
                    viewModel.add(name, pin, isWaitress)
                    name = ""
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text("Ajouter") }
        }

        Text("Permissions configurables : vendre, produits, stock, annuler, marquer payé, historique, recettes, rapports, utilisateurs, paramètres.", style = MaterialTheme.typography.bodyMedium)

        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(users, key = { it.id }) { user ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${user.name} · ${user.role}" + if (user.isWaitress) " · serveuse" else "")
                        Text(
                            user.permissions.take(6).joinToString(", "),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = { viewModel.switchUser(user) }) {
                        Text(if (user.id == current.id) "Actif" else "Utiliser")
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
