package com.climus.climeet.app.di

import com.climus.climeet.data.repository.MainRepository
import com.climus.climeet.data.repository.MainRepositoryImpl
import com.climus.climeet.data.repository.route.AndroidNetworkAvailability
import com.climus.climeet.data.repository.route.NetworkAvailability
import com.climus.climeet.data.repository.route.RetrofitRouteCatalogRemoteDataSource
import com.climus.climeet.data.repository.route.RoomRouteCatalogStore
import com.climus.climeet.data.repository.route.RouteCatalogRemoteDataSource
import com.climus.climeet.data.repository.route.RouteCatalogRepository
import com.climus.climeet.data.repository.route.RouteCatalogRepositoryImpl
import com.climus.climeet.data.repository.route.RouteCatalogStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindMainRepository(globalRepositoryImpl: MainRepositoryImpl): MainRepository

    @Binds
    abstract fun bindRouteCatalogRemoteDataSource(
        dataSource: RetrofitRouteCatalogRemoteDataSource,
    ): RouteCatalogRemoteDataSource

    @Binds
    abstract fun bindRouteCatalogStore(store: RoomRouteCatalogStore): RouteCatalogStore

    @Binds
    abstract fun bindNetworkAvailability(availability: AndroidNetworkAvailability): NetworkAvailability

    @Binds
    abstract fun bindRouteCatalogRepository(repository: RouteCatalogRepositoryImpl): RouteCatalogRepository
}
