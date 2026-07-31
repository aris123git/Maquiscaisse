package com.maquis.caisse.data.repository

import com.maquis.caisse.data.local.dao.DiningTableDao
import com.maquis.caisse.data.local.entity.DiningTableEntity
import com.maquis.caisse.domain.model.DiningTable
import com.maquis.caisse.domain.repository.TableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TableRepositoryImpl @Inject constructor(
    private val dao: DiningTableDao,
) : TableRepository {
    override fun observeActive(): Flow<List<DiningTable>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override suspend fun add(number: String, name: String, capacity: Int): Long =
        withContext(Dispatchers.IO) {
            require(number.isNotBlank()) { "Numéro de table obligatoire" }
            dao.insert(
                DiningTableEntity(
                    number = number.trim(),
                    name = name.trim(),
                    capacity = capacity.coerceAtLeast(1),
                ),
            )
        }

    override suspend fun update(table: DiningTable) = withContext(Dispatchers.IO) {
        dao.update(
            DiningTableEntity(
                id = table.id,
                number = table.number,
                name = table.name,
                capacity = table.capacity,
                status = table.status,
                isActive = table.isActive,
            ),
        )
    }

    private fun DiningTableEntity.toDomain() =
        DiningTable(id, number, name, capacity, status, isActive)
}
