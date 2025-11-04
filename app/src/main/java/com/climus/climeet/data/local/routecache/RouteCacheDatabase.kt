package com.climus.climeet.data.local.routecache

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
abstract class RouteCacheDatabase : RoomDatabase() {
    abstract fun routeCacheDao(): RouteCacheDao
}
