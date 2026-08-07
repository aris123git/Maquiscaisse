package com.maquis.caisse.domain.repository

import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.model.CaisseSession
import kotlinx.coroutines.flow.Flow

interface CaisseSessionRepository {
    /** Ouvre une nouvelle session pour cet utilisateur. */
    suspend fun openSession(user: AppUser): Long

    /** Ferme la session ouverte la plus récente (si elle existe). */
    suspend fun closeCurrentSession()

    /** Session actuellement ouverte, ou null. */
    suspend fun getOpenSession(): CaisseSession?

    /** Les 30 dernières sessions (Flow pour l'UI). */
    fun observeRecent(): Flow<List<CaisseSession>>
}
