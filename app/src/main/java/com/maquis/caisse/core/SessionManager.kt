package com.maquis.caisse.core

import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.model.Permissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utilisateur courant (session locale).
 * Par défaut : Admin (toutes permissions) pour ne pas bloquer le maquis.
 */
@Singleton
class SessionManager @Inject constructor() {
    private val _currentUser = MutableStateFlow(
        AppUser(
            id = 1L,
            name = "Admin",
            pin = "0000",
            role = "ADMIN",
            permissions = Permissions.ADMIN_DEFAULT.toSet(),
            isWaitress = false,
        ),
    )
    val currentUser: StateFlow<AppUser> = _currentUser.asStateFlow()

    fun user(): AppUser = _currentUser.value

    fun setUser(user: AppUser) {
        _currentUser.value = user
    }

    fun can(permission: String): Boolean = user().can(permission)
}
