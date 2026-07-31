package com.maquis.caisse.di

import com.maquis.caisse.data.repository.CategoryRepositoryImpl
import com.maquis.caisse.data.repository.OrderRepositoryImpl
import com.maquis.caisse.data.repository.ProductRepositoryImpl
import com.maquis.caisse.data.repository.SaleRepositoryImpl
import com.maquis.caisse.data.repository.SettingsRepositoryImpl
import com.maquis.caisse.data.repository.StockRepositoryImpl
import com.maquis.caisse.data.repository.TableRepositoryImpl
import com.maquis.caisse.data.repository.UserRepositoryImpl
import com.maquis.caisse.domain.repository.CategoryRepository
import com.maquis.caisse.domain.repository.OrderRepository
import com.maquis.caisse.domain.repository.ProductRepository
import com.maquis.caisse.domain.repository.SaleRepository
import com.maquis.caisse.domain.repository.SettingsRepository
import com.maquis.caisse.domain.repository.StockRepository
import com.maquis.caisse.domain.repository.TableRepository
import com.maquis.caisse.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository

    @Binds @Singleton
    abstract fun bindSaleRepository(impl: SaleRepositoryImpl): SaleRepository

    @Binds @Singleton
    abstract fun bindOrderRepository(impl: OrderRepositoryImpl): OrderRepository

    @Binds @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds @Singleton
    abstract fun bindTableRepository(impl: TableRepositoryImpl): TableRepository

    @Binds @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds @Singleton
    abstract fun bindStockRepository(impl: StockRepositoryImpl): StockRepository

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
