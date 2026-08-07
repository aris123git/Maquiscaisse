package com.maquis.caisse.data.repository

import com.maquis.caisse.data.local.dao.CaisseSessionDao
import com.maquis.caisse.data.local.entity.CaisseSessionEntity
import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.model.CaisseSession
import com.maquis.caisse.domain.repository.CaisseSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaisseSessionRepositoryImpl @Inject constructor(
    private val dao: CaisseSessionDao,
) : CaisseSessionRepository {

    override suspend fun openSession(user: AppUser): Long {
        val entity = CaisseSessionEntity(
            userId = user.id,
            userName = user.name,
            openedAt = System.currentTimeMillis(),
        )
        return dao.insert(entity)
    }

    override suspend fun closeCurrentSession() {
        val open = dao.getOpenSession() ?: return
        dao.closeSession(open.id, System.currentTimeMillis())
    }

    override suspend fun getOpenSession(): CaisseSession? =
        dao.getOpenSession()?.toDomain()

    override fun observeRecent(): Flow<List<CaisseSession>> =
        dao.observeRecent().map { list -> list.map { it.toDomain() } }

    private fun CaisseSessionEntity.toDomain() = CaisseSession(
        id = id,
        userId = userId,
        userName = userName,
        openedAt = openedAt,
        closedAt = closedAt,
        salesCount = salesCount,
        totalAmount = totalAmount,
    )
}
