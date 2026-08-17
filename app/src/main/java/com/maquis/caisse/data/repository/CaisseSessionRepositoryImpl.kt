package com.maquis.caisse.data.repository

import com.maquis.caisse.data.local.dao.CaisseSessionDao
import com.maquis.caisse.data.local.entity.CaisseSessionEntity
import com.maquis.caisse.domain.model.CaisseSessionInfo
import com.maquis.caisse.domain.repository.CaisseSessionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CaisseSessionRepositoryImpl @Inject constructor(
    private val dao: CaisseSessionDao,
) : CaisseSessionRepository {

    override suspend fun listOpenedBetween(
        fromMs: Long,
        toMs: Long,
        userId: Long?,
    ): List<CaisseSessionInfo> = withContext(Dispatchers.IO) {
        val rows = if (userId != null) {
            dao.listByUserAndOpenedBetween(userId, fromMs, toMs)
        } else {
            dao.listOpenedBetween(fromMs, toMs)
        }
        rows.map { it.toDomain() }
    }

    private fun CaisseSessionEntity.toDomain() = CaisseSessionInfo(
        id = id,
        userId = userId,
        userName = userName,
        openedAt = openedAt,
        closedAt = closedAt,
    )
}
