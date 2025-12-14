package com.climus.climeet.data.local.cache

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "route_catalog_metadata",
    primaryKeys = ["gymId", "queryDate"],
)
data class RouteCatalogMetadataEntity(
    val gymId: Long,
    val queryDate: String,
    val serverTimePoint: String,
    val etag: String?,
    val receivedAtEpochMs: Long,
    val freshUntilEpochMs: Long,
    val lastAccessedAtEpochMs: Long,
    val estimatedBytes: Long,
    val maxFloor: Int,
    val layoutImageUrl: String?,
    val sectorCount: Int,
    val difficultyCount: Int,
    val routeCount: Int,
)

@Entity(
    tableName = "route_sector",
    primaryKeys = ["gymId", "queryDate", "sectorId"],
    foreignKeys = [
        ForeignKey(
            entity = RouteCatalogMetadataEntity::class,
            parentColumns = ["gymId", "queryDate"],
            childColumns = ["gymId", "queryDate"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["gymId", "queryDate"])],
)
data class RouteSectorEntity(
    val gymId: Long,
    val queryDate: String,
    val sectorId: Long,
    val floor: Int,
    val name: String,
    val sectorImageUrl: String,
)

@Entity(
    tableName = "route_difficulty",
    primaryKeys = ["gymId", "queryDate", "difficulty"],
    foreignKeys = [
        ForeignKey(
            entity = RouteCatalogMetadataEntity::class,
            parentColumns = ["gymId", "queryDate"],
            childColumns = ["gymId", "queryDate"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["gymId", "queryDate"])],
)
data class RouteDifficultyEntity(
    val gymId: Long,
    val queryDate: String,
    val difficulty: Int,
    val climeetDifficultyName: String,
    val gymDifficultyName: String,
    val gymDifficultyColor: String,
)

@Entity(
    tableName = "route_catalog_item",
    primaryKeys = ["gymId", "queryDate", "routeId"],
    foreignKeys = [
        ForeignKey(
            entity = RouteCatalogMetadataEntity::class,
            parentColumns = ["gymId", "queryDate"],
            childColumns = ["gymId", "queryDate"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["gymId", "queryDate"]),
        Index(value = ["gymId", "queryDate", "sectorId"]),
        Index(value = ["gymId", "queryDate", "difficulty"]),
    ],
)
data class RouteEntity(
    val gymId: Long,
    val queryDate: String,
    val routeId: Long,
    val sectorId: Long,
    val sectorName: String,
    val climeetDifficultyName: String,
    val difficulty: Int,
    val gymDifficultyName: String,
    val gymDifficultyColor: String,
    val routeImageUrl: String,
    val holdColor: String,
)
