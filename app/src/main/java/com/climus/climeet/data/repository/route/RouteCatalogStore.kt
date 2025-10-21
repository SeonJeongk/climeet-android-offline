package com.climus.climeet.data.repository.route

import com.climus.climeet.data.local.routecache.RouteCacheDao
import com.climus.climeet.data.local.routecache.RouteCatalogMetadataEntity
import com.climus.climeet.data.local.routecache.RouteDifficultyEntity
import com.climus.climeet.data.local.routecache.RouteEntity
import com.climus.climeet.data.local.routecache.RouteSectorEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject

data class StoredCatalogMetadata(
    val etag: String?,
    val receivedAtEpochMs: Long,
    val freshUntilEpochMs: Long,
    val catalogComplete: Boolean = true,
)

data class StoredCatalog(
    val catalog: RouteCatalog?,
    val metadata: StoredCatalogMetadata?,
)

data class CatalogWrite(
    val key: RouteCatalogKey,
    val catalog: RouteCatalog,
    val etag: String?,
    val receivedAtEpochMs: Long,
    val freshUntilEpochMs: Long,
    val estimatedBytes: Long,
)

interface RouteCatalogStore {
    fun observe(
        key: RouteCatalogKey,
        filter: RouteFilter,
    ): Flow<StoredCatalog>

    suspend fun metadata(key: RouteCatalogKey): StoredCatalogMetadata?

    suspend fun replace(write: CatalogWrite)

    suspend fun touchFreshness(
        key: RouteCatalogKey,
        receivedAtEpochMs: Long,
        freshUntilEpochMs: Long,
    )

    suspend fun touchAccess(
        key: RouteCatalogKey,
        accessedAtEpochMs: Long,
    )

    suspend fun entries(): List<CacheEntry>

    suspend fun delete(key: RouteCatalogKey)
}

class RoomRouteCatalogStore @Inject constructor(
    private val dao: RouteCacheDao,
) : RouteCatalogStore {
    override fun observe(
        key: RouteCatalogKey,
        filter: RouteFilter,
    ): Flow<StoredCatalog> {
        val date = key.queryDate.toString()
        return combine(
            dao.observeMetadata(key.gymId, date),
            dao.observeSectors(key.gymId, date),
            dao.observeDifficulties(key.gymId, date),
            dao.observeRoutes(key.gymId, date, filter.floor, filter.sectorId, filter.difficulty),
            dao.observeRouteCount(key.gymId, date),
        ) { metadata, sectors, difficulties, routes, routeCount ->
            val catalogComplete =
                metadata != null &&
                    metadata.sectorCount == sectors.size &&
                    metadata.difficultyCount == difficulties.size &&
                    metadata.routeCount == routeCount
            StoredCatalog(
                catalog = metadata?.takeIf { catalogComplete }?.let {
                    RouteCatalog(
                        maxFloor = it.maxFloor,
                        layoutImageUrl = it.layoutImageUrl,
                        serverTimePoint = it.serverTimePoint,
                        sectors = sectors.map(RouteSectorEntity::toDomain),
                        difficulties = difficulties.map(RouteDifficultyEntity::toDomain),
                        routes = routes.map(RouteEntity::toDomain),
                    )
                },
                metadata = metadata?.toStoredMetadata(catalogComplete),
            )
        }
    }

    override suspend fun metadata(key: RouteCatalogKey): StoredCatalogMetadata? =
        dao.catalogIntegrity(key.gymId, key.queryDate.toString())?.let {
            it.metadata.toStoredMetadata(it.isComplete)
        }

    override suspend fun replace(write: CatalogWrite) {
        val key = write.key
        val date = key.queryDate.toString()
        dao.replaceCatalog(
            metadata = RouteCatalogMetadataEntity(
                gymId = key.gymId,
                queryDate = date,
                serverTimePoint = write.catalog.serverTimePoint,
                etag = write.etag,
                receivedAtEpochMs = write.receivedAtEpochMs,
                freshUntilEpochMs = write.freshUntilEpochMs,
                lastAccessedAtEpochMs = write.receivedAtEpochMs,
                estimatedBytes = write.estimatedBytes,
                maxFloor = write.catalog.maxFloor,
                layoutImageUrl = write.catalog.layoutImageUrl,
                sectorCount = write.catalog.sectors.size,
                difficultyCount = write.catalog.difficulties.size,
                routeCount = write.catalog.routes.size,
            ),
            sectors = write.catalog.sectors.map {
                RouteSectorEntity(
                    key.gymId,
                    date,
                    it.sectorId,
                    it.floor,
                    it.name,
                    it.sectorImageUrl,
                )
            },
            difficulties = write.catalog.difficulties.map {
                RouteDifficultyEntity(
                    key.gymId,
                    date,
                    it.difficulty,
                    it.climeetDifficultyName,
                    it.gymDifficultyName,
                    it.gymDifficultyColor,
                )
            },
            routes = write.catalog.routes.map {
                RouteEntity(
                    key.gymId,
                    date,
                    it.routeId,
                    it.sectorId,
                    it.sectorName,
                    it.climeetDifficultyName,
                    it.difficulty,
                    it.gymDifficultyName,
                    it.gymDifficultyColor,
                    it.routeImageUrl,
                    it.holdColor,
                )
            },
        )
    }

    override suspend fun touchFreshness(
        key: RouteCatalogKey,
        receivedAtEpochMs: Long,
        freshUntilEpochMs: Long,
    ) {
        dao.touchFreshness(key.gymId, key.queryDate.toString(), receivedAtEpochMs, freshUntilEpochMs)
    }

    override suspend fun touchAccess(
        key: RouteCatalogKey,
        accessedAtEpochMs: Long,
    ) {
        dao.touchAccess(key.gymId, key.queryDate.toString(), accessedAtEpochMs)
    }

    override suspend fun entries(): List<CacheEntry> =
        dao.cacheEntries().map {
            CacheEntry(
                RouteCatalogKey(it.gymId, LocalDate.parse(it.queryDate)),
                it.estimatedBytes,
                it.lastAccessedAtEpochMs,
            )
        }

    override suspend fun delete(key: RouteCatalogKey) {
        dao.deleteCatalog(key.gymId, key.queryDate.toString())
    }
}

private fun RouteCatalogMetadataEntity.toStoredMetadata(catalogComplete: Boolean) =
    StoredCatalogMetadata(etag, receivedAtEpochMs, freshUntilEpochMs, catalogComplete)

private fun RouteSectorEntity.toDomain() = RouteSector(sectorId, floor, name, sectorImageUrl)

private fun RouteDifficultyEntity.toDomain() =
    RouteDifficulty(difficulty, climeetDifficultyName, gymDifficultyName, gymDifficultyColor)

private fun RouteEntity.toDomain() =
    CachedRoute(
        routeId,
        sectorId,
        sectorName,
        climeetDifficultyName,
        difficulty,
        gymDifficultyName,
        gymDifficultyColor,
        routeImageUrl,
        holdColor,
    )
