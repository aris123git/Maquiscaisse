package com.maquis.caisse.data.repository

import com.maquis.caisse.data.local.dao.AvoirDao
import com.maquis.caisse.data.local.entity.AvoirEntity
import com.maquis.caisse.domain.model.Avoir
import com.maquis.caisse.domain.repository.AvoirRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvoirRepositoryImpl @Inject constructor(
    private val dao: AvoirDao,
) : AvoirRepository {

    override fun observeAll(): Flow<List<Avoir>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun createAvoir(
        orderId: Long?,
        orderPublicId: String?,
        customerName: String,
        reason: String,
        amount: Long,
        userId: Long?,
        userName: String,
        note: String,
    ): Avoir {
        val createdAt = System.currentTimeMillis()
        val entity = AvoirEntity(
            orderId = orderId,
            orderPublicId = orderPublicId,
            customerName = customerName,
            reason = reason,
            amount = amount,
            createdAt = createdAt,
            userId = userId,
            userName = userName,
            note = note,
        )
        val id = dao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    private fun AvoirEntity.toDomain() = Avoir(
        id = id,
        orderId = orderId,
        orderPublicId = orderPublicId,
        customerName = customerName,
        reason = reason,
        amount = amount,
        createdAt = createdAt,
        userName = userName,
        note = note,
    )
}
