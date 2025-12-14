package com.climus.climeet.data.local.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class RouteCatalogIntegrity(
    val metadata: RouteCatalogMetadataEntity,
    val sectorCount: Int,
    val difficultyCount: Int,
    val routeCount: Int,
) {
    val isComplete: Boolean
        get() =
            metadata.sectorCount == sectorCount &&
                metadata.difficultyCount == difficultyCount &&
                metadata.routeCount == routeCount
}

@Dao
abstract class RouteCacheDao {
    @Query("SELECT * FROM route_catalog_metadata WHERE gymId = :gymId AND queryDate = :queryDate")
    abstract fun observeMetadata(
        gymId: Long,
        queryDate: String,
    ): Flow<RouteCatalogMetadataEntity?>

    @Query("SELECT * FROM route_catalog_metadata WHERE gymId = :gymId AND queryDate = :queryDate")
    abstract suspend fun metadata(
        gymId: Long,
        queryDate: String,
    ): RouteCatalogMetadataEntity?

    @Query("SELECT COUNT(*) FROM route_sector WHERE gymId = :gymId AND queryDate = :queryDate")
    abstract suspend fun sectorCount(
        gymId: Long,
        queryDate: String,
    ): Int

    @Query("SELECT COUNT(*) FROM route_difficulty WHERE gymId = :gymId AND queryDate = :queryDate")
    abstract suspend fun difficultyCount(
        gymId: Long,
        queryDate: String,
    ): Int

    @Query("SELECT COUNT(*) FROM route_catalog_item WHERE gymId = :gymId AND queryDate = :queryDate")
    abstract suspend fun routeCount(
        gymId: Long,
        queryDate: String,
    ): Int

    @Query("SELECT COUNT(*) FROM route_catalog_item WHERE gymId = :gymId AND queryDate = :queryDate")
    abstract fun observeRouteCount(
        gymId: Long,
        queryDate: String,
    ): Flow<Int>

    @Transaction
    open suspend fun catalogIntegrity(
        gymId: Long,
        queryDate: String,
    ): RouteCatalogIntegrity? {
        val metadata = metadata(gymId, queryDate) ?: return null
        return RouteCatalogIntegrity(
            metadata = metadata,
            sectorCount = sectorCount(gymId, queryDate),
            difficultyCount = difficultyCount(gymId, queryDate),
            routeCount = routeCount(gymId, queryDate),
        )
    }

    @Query(
        """
        SELECT * FROM route_catalog_item
        WHERE gymId = :gymId AND queryDate = :queryDate AND (:floor <= 0 OR sectorId IN (
            SELECT sectorId FROM route_sector
            WHERE gymId = :gymId AND queryDate = :queryDate AND floor = :floor
        ))
        AND (:sectorId IS NULL OR sectorId = :sectorId)
        AND (:difficulty IS NULL OR difficulty = :difficulty)
        ORDER BY routeId
        """,
    )
    abstract fun observeRoutes(
        gymId: Long,
        queryDate: String,
        floor: Int,
        sectorId: Long?,
        difficulty: Int?,
    ): Flow<List<RouteEntity>>

    @Query("SELECT * FROM route_sector WHERE gymId = :gymId AND queryDate = :queryDate ORDER BY floor, sectorId")
    abstract fun observeSectors(
        gymId: Long,
        queryDate: String,
    ): Flow<List<RouteSectorEntity>>

    @Query("SELECT * FROM route_difficulty WHERE gymId = :gymId AND queryDate = :queryDate ORDER BY difficulty")
    abstract fun observeDifficulties(
        gymId: Long,
        queryDate: String,
    ): Flow<List<RouteDifficultyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertMetadata(entity: RouteCatalogMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSectors(entities: List<RouteSectorEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertDifficulties(entities: List<RouteDifficultyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertRoutes(entities: List<RouteEntity>)

    @Query("DELETE FROM route_catalog_metadata WHERE gymId = :gymId AND queryDate = :queryDate")
    abstract suspend fun deleteCatalog(
        gymId: Long,
        queryDate: String,
    )

    @Query("DELETE FROM route_catalog_metadata")
    abstract suspend fun deleteAllCatalogs()

    @Query("UPDATE route_catalog_metadata SET freshUntilEpochMs = :expiredAtEpochMs")
    abstract suspend fun expireAllCatalogs(expiredAtEpochMs: Long)

    @Transaction
    open suspend fun replaceCatalog(
        metadata: RouteCatalogMetadataEntity,
        sectors: List<RouteSectorEntity>,
        difficulties: List<RouteDifficultyEntity>,
        routes: List<RouteEntity>,
    ) {
        deleteCatalog(metadata.gymId, metadata.queryDate)
        insertMetadata(metadata)
        insertSectors(sectors)
        insertDifficulties(difficulties)
        insertRoutes(routes)
    }

    @Query(
        """
        UPDATE route_catalog_metadata
        SET receivedAtEpochMs = :receivedAtEpochMs,
            freshUntilEpochMs = :freshUntilEpochMs,
            lastAccessedAtEpochMs = :receivedAtEpochMs
        WHERE gymId = :gymId AND queryDate = :queryDate
        """,
    )
    abstract suspend fun touchFreshness(
        gymId: Long,
        queryDate: String,
        receivedAtEpochMs: Long,
        freshUntilEpochMs: Long,
    ): Int

    @Query(
        "UPDATE route_catalog_metadata SET lastAccessedAtEpochMs = :accessedAtEpochMs " +
            "WHERE gymId = :gymId AND queryDate = :queryDate",
    )
    abstract suspend fun touchAccess(
        gymId: Long,
        queryDate: String,
        accessedAtEpochMs: Long,
    )

    @Query("SELECT * FROM route_catalog_metadata ORDER BY lastAccessedAtEpochMs")
    abstract suspend fun cacheEntries(): List<RouteCatalogMetadataEntity>
}
