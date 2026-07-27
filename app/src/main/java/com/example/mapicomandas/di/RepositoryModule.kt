package com.example.mapicomandas.di

import com.example.mapicomandas.data.repository.RestauranteRepository
import com.example.mapicomandas.data.repository.RestauranteRepositoryHttpImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    // Dominio de restaurante = API central (https://api.mapi.codesi.mx), NO jTDS.
    // jTDS queda acotado y documentado SOLO para NetPay/CFDI (PagosNetPay,
    // SolicitudesFacturaApp) hasta que el servidor exponga esas piezas.
    @Binds
    @Singleton
    abstract fun bindRestauranteRepository(
        impl: RestauranteRepositoryHttpImpl
    ): RestauranteRepository
}
