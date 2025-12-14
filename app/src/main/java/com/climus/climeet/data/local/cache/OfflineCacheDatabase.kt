package com.climus.climeet.data.local.cache

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        RouteCatalogMetadataEntity::class,
        RouteSectorEntity::class,
        RouteDifficultyEntity::class,
        RouteEntity::class,
        HomeGymCacheMetadataEntity::class,
        HomeGymEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class OfflineCacheDatabase : RoomDatabase() {
    abstract fun routeCacheDao(): RouteCacheDao

    abstract fun homeGymCacheDao(): HomeGymCacheDao
}
