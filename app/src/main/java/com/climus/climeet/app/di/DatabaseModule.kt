package com.climus.climeet.app.di

import android.content.Context
import androidx.room.Room
import com.climus.climeet.data.local.ClimbingDatabase
import com.climus.climeet.data.local.ClimbingRecordDao
import com.climus.climeet.data.local.RouteRecordDao
import com.climus.climeet.data.local.routecache.RouteCacheDao
import com.climus.climeet.data.local.routecache.RouteCacheDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DATABASE_NAME = "climeet.db"
    private const val ROUTE_CACHE_DATABASE_NAME = "route_catalog_cache.db"

    @Provides
    @Singleton
    fun provideRecordDatabase(
        @ApplicationContext context: Context,
    ): ClimbingDatabase =
        Room.databaseBuilder(context, ClimbingDatabase::class.java, DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideClimbingRecordDao(recordDatabase: ClimbingDatabase): ClimbingRecordDao =
        recordDatabase.ClimbingRecordDao()

    @Provides
    @Singleton
    fun provideRouteRecordDao(recordDatabase: ClimbingDatabase): RouteRecordDao = recordDatabase.RouteRecordDao()

    @Provides
    @Singleton
    fun provideRouteCacheDatabase(
        @ApplicationContext context: Context,
    ): RouteCacheDatabase =
        Room.databaseBuilder(context, RouteCacheDatabase::class.java, ROUTE_CACHE_DATABASE_NAME)
            .build()

    @Provides
    @Singleton
    fun provideRouteCacheDao(database: RouteCacheDatabase): RouteCacheDao = database.routeCacheDao()

    @Provides
    fun provideClock(): Clock = Clock.systemUTC()
}
