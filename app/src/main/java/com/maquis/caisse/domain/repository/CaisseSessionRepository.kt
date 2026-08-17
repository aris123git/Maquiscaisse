package com.maquis.caisse.domain.repository

import com.maquis.caisse.domain.model.CaisseSessionInfo

interface CaisseSessionRepository {
    suspend fun listOpenedBetween(
        fromMs: Long,
        toMs: Long,
        userId: Long? = null,
    ): List<CaisseSessionInfo>
}
