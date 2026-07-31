package com.maquis.caisse.ui.login

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.repository.UserRepository
import com.maquis.caisse.ui.common.DropdownField
import com.maquis.caisse.ui.theme.GestionBlue
import com.maquis.caisse.ui.theme.GestionCyan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val session: SessionManager,
) : ViewModel() {
    val users: StateFlow<List<AppUser>> = userRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun login(name: String, pin: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _error.value = null
            if (name.isBlank() || pin.length < 4) {
                _error.value = "Choisis un compte et saisis ton code PIN"
                return@launch
            }
            val user = userRepository.login(name, pin)
            if (user == null) {
                _error.value = "Compte ou code incorrect"
                return@launch
            }
            session.setUser(user)
            onSuccess()
        }
    }
}

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var selectedName by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }

    val pulse = rememberInfiniteTransition(label = "loginPulse")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(glow)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            GestionCyan.copy(alpha = 0.25f),
                            GestionBlue.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.92f),
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Maquis Caisse", style = MaterialTheme.typography.headlineMedium, color = GestionBlue)
                    Text(
                        "Connecte-toi avec ton compte.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DropdownField(
                        label = "Compte",
                        selected = selectedName ?: users.firstOrNull()?.name,
                        options = users.map { it.name },
                        optionLabel = { it },
                        onSelect = { selectedName = it },
                        allowNull = false,
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("Code PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = {
                            val name = selectedName ?: users.firstOrNull()?.name.orEmpty()
                            viewModel.login(name, pin, onLoggedIn)
                        },
                        enabled = (selectedName ?: users.firstOrNull()?.name) != null && pin.length >= 4,
                        colors = ButtonDefaults.buttonColors(containerColor = GestionBlue),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    ) {
                        Text("Entrer")
                    }
                }
            }
        }
    }
}
