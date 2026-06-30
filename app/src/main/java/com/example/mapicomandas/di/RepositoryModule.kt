package com.example.mapicomandas.di

import com.example.mapicomandas.data.repository.RestauranteRepository
import com.example.mapicomandas.data.repository.RestauranteRepositoryImpl
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
    abstract fun bindRestauranteRepository(
        impl: RestauranteRepositoryImpl
    ): RestauranteRepository
}
