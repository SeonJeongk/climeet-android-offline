package com.climus.climeet.data.local.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteCacheDaoTest {
    private lateinit var database: OfflineCacheDatabase
    private lateinit var dao: RouteCacheDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OfflineCacheDatabase::class.java,
        ).build()
        dao = database.routeCacheDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun existingCatalog_replaceCatalog_removesOldRows() =
        runBlocking {
            dao.replaceCatalog(
                metadata("v1"),
                sectors = listOf(sector(10L, 1), sector(20L, 2)),
                difficulties = listOf(difficulty(1), difficulty(2)),
                routes = listOf(route(1L, 10L, 1), route(2L, 20L, 2)),
            )

            assertEquals(listOf(1L, 2L), dao.observeRoutes(GYM_ID, DATE).first().map(RouteEntity::routeId))

            dao.replaceCatalog(
                metadata("v2"),
                sectors = listOf(sector(30L, 1)),
                difficulties = listOf(difficulty(3)),
                routes = listOf(route(3L, 30L, 3)),
            )

            assertEquals(listOf(3L), dao.observeRoutes(GYM_ID, DATE).first().map(RouteEntity::routeId))
            assertEquals(listOf(30L), dao.observeSectors(GYM_ID, DATE).first().map(RouteSectorEntity::sectorId))
            assertEquals(
                listOf(3),
                dao.observeDifficulties(GYM_ID, DATE).first().map(RouteDifficultyEntity::difficulty),
            )
            assertEquals("v2", dao.metadata(GYM_ID, DATE)?.etag)
            assertTrue(dao.catalogIntegrity(GYM_ID, DATE)?.isComplete == true)
        }

    @Test
    fun incompleteCatalog_catalogIntegrity_detectsMissingRows() =
        runBlocking {
            dao.replaceCatalog(
                metadata("v1", sectorCount = 2, difficultyCount = 1, routeCount = 2),
                sectors = listOf(sector(10L, 1)),
                difficulties = listOf(difficulty(1)),
                routes = listOf(route(1L, 10L, 1)),
            )

            assertFalse(dao.catalogIntegrity(GYM_ID, DATE)?.isComplete ?: true)
        }

    private fun metadata(
        etag: String,
        sectorCount: Int = if (etag == "v1") 2 else 1,
        difficultyCount: Int = if (etag == "v1") 2 else 1,
        routeCount: Int = if (etag == "v1") 2 else 1,
    ) = RouteCatalogMetadataEntity(
        gymId = GYM_ID,
        queryDate = DATE,
        serverTimePoint = DATE,
        etag = etag,
        receivedAtEpochMs = 1L,
        freshUntilEpochMs = 2L,
        lastAccessedAtEpochMs = 1L,
        estimatedBytes = 100L,
        maxFloor = 2,
        layoutImageUrl = null,
        sectorCount = sectorCount,
        difficultyCount = difficultyCount,
        routeCount = routeCount,
    )

    private fun sector(
        sectorId: Long,
        floor: Int,
    ) = RouteSectorEntity(GYM_ID, DATE, sectorId, floor, "sector-$sectorId", "")

    private fun difficulty(value: Int) = RouteDifficultyEntity(GYM_ID, DATE, value, "V$value", "V$value", "#000000")

    private fun route(
        routeId: Long,
        sectorId: Long,
        difficulty: Int,
    ) = RouteEntity(
        GYM_ID,
        DATE,
        routeId,
        sectorId,
        "sector-$sectorId",
        "V$difficulty",
        difficulty,
        "V$difficulty",
        "#000000",
        "",
        "red",
    )

    private companion object {
        const val GYM_ID = 1L
        const val DATE = "2026-08-12"
    }
}
