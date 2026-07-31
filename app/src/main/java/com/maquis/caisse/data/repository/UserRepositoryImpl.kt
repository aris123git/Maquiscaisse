package com.maquis.caisse.data.repository

import com.maquis.caisse.data.local.dao.UserDao
import com.maquis.caisse.data.local.entity.UserEntity
import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val dao: UserDao,
) : UserRepository {
    override fun observeActive(): Flow<List<AppUser>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeWaitresses(): Flow<List<AppUser>> =
        dao.observeWaitresses().map { list -> list.map { it.toDomain() } }

    override suspend fun listWaitresses(): List<AppUser> = withContext(Dispatchers.IO) {
        dao.listWaitresses().map { it.toDomain() }
    }

    override suspend fun add(user: AppUser): Long = withContext(Dispatchers.IO) {
        dao.insert(user.toEntity())
    }

    override suspend fun update(user: AppUser) = withContext(Dispatchers.IO) {
        dao.update(user.toEntity())
    }

    override suspend fun login(name: String, pin: String): AppUser? = withContext(Dispatchers.IO) {
        dao.login(name.trim(), pin)?.toDomain()
    }

    private fun UserEntity.toDomain() = AppUser(
        id = id,
        name = name,
        pin = pin,
        role = role,
        permissions = permissions.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
        isActive = isActive,
        isWaitress = isWaitress,
    )

    private fun AppUser.toEntity() = UserEntity(
        id = id,
        name = name.trim(),
        pin = pin,
        role = role,
        permissions = permissions.joinToString(","),
        isActive = isActive,
        isWaitress = isWaitress,
    )
}
