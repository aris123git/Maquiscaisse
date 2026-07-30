package com.maquis.caisse.di

import com.maquis.caisse.data.repository.ProductRepositoryImpl
import com.maquis.caisse.data.repository.SaleRepositoryImpl
import com.maquis.caisse.domain.repository.ProductRepository
import com.maquis.caisse.domain.repository.SaleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository

    @Binds
    @Singleton
    abstract fun bindSaleRepository(impl: SaleRepositoryImpl): SaleRepository
}
