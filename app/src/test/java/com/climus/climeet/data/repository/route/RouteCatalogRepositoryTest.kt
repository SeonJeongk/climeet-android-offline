package com.climus.climeet.data.repository.route

import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.model.ErrorType
import com.climus.climeet.data.model.response.DifficultyItem
import com.climus.climeet.data.model.response.GetGymFilteringKeyResponse
import com.climus.climeet.data.model.response.RouteItem
import com.climus.climeet.data.model.response.SectorItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

class RouteCatalogRepositoryTest {
    private val key = RouteCatalogKey(1L, LocalDate.parse("2026-08-12"))
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)

    @Test
    fun freshCatalog_refresh_skipsNetwork() =
        runTest {
            val store = FakeStore(metadata = StoredCatalogMetadata("v1", 0L, 30_000L))
            val remote = FakeRemote(mutableListOf())

            assertTrue(repository(remote, store).refresh(key) is RefreshResult.SkippedFresh)

            assertEquals(0, remote.keyCalls)
        }

    @Test
    fun staleCatalog_refreshNotModified_extendsFreshnessOnly() =
        runTest {
            val store = FakeStore(metadata = StoredCatalogMetadata("v1", 0L, 0L))
            val repository =
                repository(
                    FakeRemote(mutableListOf(FilteringKeyResponse.NotModified("max-age=30"))),
                    store,
                )

            assertTrue(repository.refresh(key) is RefreshResult.NotModified)

            assertNull(store.lastWrite)
            assertEquals(1_000L, store.metadata(key)?.receivedAtEpochMs)
            assertEquals(31_000L, store.metadata(key)?.freshUntilEpochMs)
        }

    @Test
    fun incompleteCatalog_refreshNotModified_refetchesWithoutEtagAndReplacesCatalog() =
        runTest {
            val store = FakeStore(metadata = StoredCatalogMetadata("old", 0L, 0L, catalogComplete = false))
            val remote = FakeRemote(
                keyResponses = mutableListOf(
                    FilteringKeyResponse.NotModified("max-age=30"),
                    modified("new"),
                    FilteringKeyResponse.NotModified("max-age=15"),
                ),
                pages = mutableListOf(RoutePageResponse.Success(0, false, listOf(route(1)))),
            )

            assertTrue(repository(remote, store).refresh(key) is RefreshResult.Updated)

            assertEquals(listOf("old", null, "new"), remote.requestEtags)
            assertEquals("new", store.lastWrite?.etag)
            assertEquals(1, store.replaceCount)
        }

    @Test
    fun cachedCatalog_observe_updatesLruAccess() =
        runTest {
            val store = FakeStore(metadata = StoredCatalogMetadata("v1", 0L, 30_000L))
            val repository = repository(FakeRemote(mutableListOf()), store)

            repository.observe(key).first()

            assertEquals(1_000L, store.lastAccessedAtEpochMs)
        }

    @Test
    fun overCapacityCache_observe_retriesTrimming() =
        runTest {
            val oldest = RouteCatalogKey(2, key.queryDate)
            val newest = RouteCatalogKey(3, key.queryDate)
            val store = FakeStore(
                metadata = StoredCatalogMetadata("v1", 0L, 30_000L),
                entries = mutableListOf(
                    CacheEntry(oldest, 15L * 1024L * 1024L, 0L),
                    CacheEntry(newest, 10L * 1024L * 1024L, 1L),
                ),
            )
            val repository = repository(FakeRemote(mutableListOf()), store)

            repository.observe(key).first()

            assertEquals(listOf(oldest), store.deleted)
        }

    @Test
    fun staleCatalog_observeOffline_showsContentWithoutErrorOrNetwork() =
        runTest {
            val store = FakeStore(metadata = StoredCatalogMetadata("v1", 0L, 0L), catalog = catalog())
            val remote = FakeRemote(mutableListOf())
            val repository = repository(remote, store, offline = true)

            val state = repository.observe(key).first()

            assertTrue(state is RouteCatalogState.Content)
            assertEquals(Freshness.STALE, (state as RouteCatalogState.Content).freshness)
            assertEquals(0, remote.keyCalls)
        }

    @Test
    fun missingCatalog_observeOffline_failsOnceWithoutNetwork() =
        runTest {
            val remote = FakeRemote(mutableListOf())
            val repository = repository(remote, FakeStore(), offline = true)
            val failures = mutableListOf<RouteCatalogState.Failed>()

            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.observe(key).filterIsInstance<RouteCatalogState.Failed>().collect { failures += it }
            }
            repository.refresh(key)
            job.cancelAndJoin()

            assertEquals("OFFLINE", failures.single().error.code)
            assertEquals(0, remote.keyCalls)
        }

    @Test
    fun failedCatalog_observeAgain_startsWithoutPreviousError() =
        runTest {
            val error = BaseState.Error("실패", "HTTP_503", type = ErrorType.HTTP, httpStatus = 503)
            val repository = repository(
                FakeRemote(
                    mutableListOf(
                        FilteringKeyResponse.Failed(error),
                        modified("new"),
                        FilteringKeyResponse.NotModified("max-age=15"),
                    ),
                    mutableListOf(RoutePageResponse.Success(0, false, listOf(route(1)))),
                ),
                FakeStore(),
            )

            val failed = repository.observe(key).first { it is RouteCatalogState.Failed }
            val recovered = repository.observe(key).first { it !is RouteCatalogState.Failed }

            assertEquals(error, (failed as RouteCatalogState.Failed).error)
            assertFalse(recovered is RouteCatalogState.Failed)
        }

    @Test
    fun metadataReadFailure_refresh_reportsLocalStorageWithoutThrowing() =
        runTest {
            val store = FakeStore(failures = StoreFailures(metadata = IllegalStateException("database closed")))
            val repository = repository(FakeRemote(mutableListOf()), store)

            val result = repository.refresh(key)

            assertEquals("LOCAL_STORAGE", (result as RefreshResult.FailedNoData).error.code)
        }

    @Test
    fun storeReadFailure_observe_emitsFailedWithoutThrowing() =
        runTest {
            val store = FakeStore(failures = StoreFailures(observe = IllegalStateException("database closed")))
            val repository = repository(FakeRemote(mutableListOf()), store)

            val state = repository.observe(key).first()

            assertEquals("LOCAL_STORAGE", (state as RouteCatalogState.Failed).error.code)
        }

    @Test
    fun accessUpdateFailure_observe_stillReadsCatalog() =
        runTest {
            val store = FakeStore(
                metadata = StoredCatalogMetadata("v1", 0L, 30_000L),
                catalog = catalog(),
                failures = StoreFailures(touch = IllegalStateException("database full")),
            )
            val repository = repository(FakeRemote(mutableListOf()), store)

            assertTrue(repository.observe(key).first() is RouteCatalogState.Content)
        }

    @Test
    fun earlierRegisteredCatalog_refresh_isStored() =
        runTest {
            val store = FakeStore()
            val remote = FakeRemote(
                mutableListOf(modified("v1", timePoint = "2026-08-01"), FilteringKeyResponse.NotModified("max-age=15")),
                mutableListOf(RoutePageResponse.Success(0, false, listOf(route(1)))),
            )

            assertTrue(repository(remote, store).refresh(key) is RefreshResult.Updated)
            assertEquals("2026-08-01", store.lastWrite?.catalog?.serverTimePoint)
        }

    @Test
    fun laterRegisteredCatalog_refresh_failsAsDateMismatch() =
        runTest {
            val store = FakeStore()
            val remote = FakeRemote(mutableListOf(modified("v1", timePoint = "2026-08-13")))

            val result = repository(remote, store).refresh(key)

            assertEquals("DATE_MISMATCH", (result as RefreshResult.FailedNoData).error.code)
            assertNull(store.lastWrite)
        }

    @Test
    fun existingCatalog_refreshPageFailure_preservesCatalog() =
        runTest {
            val store = FakeStore(metadata = StoredCatalogMetadata("old", 0L, 0L))
            val remote = FakeRemote(
                keyResponses = mutableListOf(modified("new")),
                pages = mutableListOf(
                    RoutePageResponse.Success(0, true, listOf(route(1))),
                    RoutePageResponse.Failed(
                        BaseState.Error(
                            "실패",
                            "HTTP_503",
                            type = ErrorType.HTTP,
                            httpStatus = 503,
                        ),
                    ),
                ),
            )

            assertTrue(repository(remote, store).refresh(key) is RefreshResult.KeptStale)
            assertNull(store.lastWrite)
            assertEquals("old", store.metadata(key)?.etag)
        }

    @Test
    fun existingCatalog_refreshTimeout_doesNotRetry() =
        runTest {
            val store = FakeStore(metadata = StoredCatalogMetadata("old", 0L, 0L))
            val remote = FakeRemote(
                keyResponses = mutableListOf(
                    FilteringKeyResponse.Failed(
                        BaseState.Error("시간 초과", "TIMEOUT", type = ErrorType.TIMEOUT),
                    ),
                ),
            )

            assertTrue(repository(remote, store).refresh(key) is RefreshResult.KeptStale)

            assertEquals(1, remote.keyCalls)
            assertNull(store.lastWrite)
            assertEquals("old", store.metadata(key)?.etag)
        }

    @Test
    fun existingCatalog_replaceFails_keepsCatalogAndStopsRefreshing() =
        runTest {
            val store = FakeStore(
                metadata = StoredCatalogMetadata("old", 0L, 0L),
                replaceFailure = IllegalStateException("database full"),
            )
            val remote = FakeRemote(
                keyResponses = mutableListOf(
                    modified("new"),
                    FilteringKeyResponse.NotModified("max-age=15"),
                ),
                pages = mutableListOf(RoutePageResponse.Success(0, false, listOf(route(1)))),
            )
            val repository = repository(remote, store)

            val result = repository.refresh(key)

            assertEquals("LOCAL_STORAGE", (result as RefreshResult.KeptStale).error.code)
            assertEquals("old", store.metadata(key)?.etag)
            assertNull(store.lastWrite)
        }

    @Test
    fun twoFloorCatalog_refresh_collectsAllPagesBeforeReplace() =
        runTest {
            val store = FakeStore()
            val remote = FakeRemote(
                keyResponses = mutableListOf(
                    modified("new", maxFloor = 2),
                    FilteringKeyResponse.NotModified("max-age=15"),
                ),
                pages = mutableListOf(
                    RoutePageResponse.Success(0, true, listOf(route(1))),
                    RoutePageResponse.Success(1, false, listOf(route(2))),
                    RoutePageResponse.Success(0, false, listOf(route(3))),
                ),
            )

            assertTrue(repository(remote, store).refresh(key) is RefreshResult.Updated)

            assertEquals(1, store.replaceCount)
            assertEquals(listOf(1L, 2L, 3L), store.lastWrite?.catalog?.routes?.map(CachedRoute::routeId))
            assertEquals(listOf(1 to 0, 1 to 1, 2 to 0), remote.pageRequests)
        }

    @Test
    fun catalogChangesDuringPaging_refresh_storesOnlySecondVersion() =
        runTest {
            val store = FakeStore()
            val remote = FakeRemote(
                keyResponses = mutableListOf(
                    modified("v1"),
                    modified("v2"),
                    FilteringKeyResponse.NotModified("max-age=15"),
                ),
                pages = mutableListOf(
                    RoutePageResponse.Success(0, false, listOf(route(1))),
                    RoutePageResponse.Success(0, false, listOf(route(2))),
                ),
            )

            assertTrue(repository(remote, store).refresh(key) is RefreshResult.Updated)

            assertEquals("v2", store.lastWrite?.etag)
            assertEquals(listOf(2L), store.lastWrite?.catalog?.routes?.map(CachedRoute::routeId))
            assertEquals(1, store.replaceCount)
        }

    @Test
    fun sameKeyTwentyCallers_refresh_shareOneRequest() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val remote = BlockingRemote(gate)
            val store = FakeStore()
            val repository = repository(remote, store)

            val calls = List(20) { async { repository.refresh(key) } }
            remote.started.await()
            gate.complete(Unit)
            calls.forEach { assertTrue(it.await() is RefreshResult.Updated) }

            assertEquals(2, remote.keyCalls.get())
            assertEquals(1, remote.pageCalls.get())
            assertEquals(1, store.replaceCount)
        }

    @Test
    fun oneCallerCancelled_refresh_completesForRemainingCaller() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val remote = BlockingRemote(gate)
            val store = FakeStore()
            val repository = repository(remote, store)

            val cancelled = async { repository.refresh(key) }
            remote.started.await()
            val survivor = async { repository.refresh(key) }
            cancelled.cancelAndJoin()
            gate.complete(Unit)

            assertTrue(survivor.await() is RefreshResult.Updated)
            assertEquals(1, store.replaceCount)
        }

    @Test
    fun observedCatalog_refresh_evictsOnlyUnobservedCatalog() =
        runTest {
            val protectedKey = RouteCatalogKey(2, key.queryDate)
            val oldKey = RouteCatalogKey(3, key.queryDate)
            val store = FakeStore(
                freshKeys = setOf(protectedKey),
                entries = mutableListOf(
                    CacheEntry(protectedKey, 15L * 1024L * 1024L, 0L),
                    CacheEntry(oldKey, 10L * 1024L * 1024L, 1L),
                ),
            )
            val remote = FakeRemote(
                mutableListOf(modified("new"), FilteringKeyResponse.NotModified("max-age=15")),
                mutableListOf(RoutePageResponse.Success(0, false, listOf(route(1)))),
            )
            val repository = repository(remote, store)
            val observer = launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.observe(protectedKey).collect {}
            }

            repository.refresh(key)
            observer.cancelAndJoin()

            assertFalse(protectedKey in store.deleted)
            assertEquals(oldKey, store.deleted.single())
        }

    private fun repository(
        remote: RouteCatalogRemoteDataSource,
        store: FakeStore,
        offline: Boolean = false,
    ) = RouteCatalogRepositoryImpl(
        remote,
        store,
        RouteCachePolicy(clock),
        clock,
        object : NetworkAvailability {
            override fun isDefinitelyOffline() = offline
        },
    )

    private fun modified(
        etag: String,
        maxFloor: Int = 1,
        timePoint: String = key.queryDate.toString(),
    ) = FilteringKeyResponse.Modified(
        body = GetGymFilteringKeyResponse(
            difficultyList = listOf(DifficultyItem("V1", 1, "#fff", "V1")),
            maxFloor = maxFloor,
            gymId = 1,
            sectorList = listOf(SectorItem(10, 1, "A", "")),
            timePoint = timePoint,
        ),
        etag = etag,
        cacheControl = "max-age=15",
    )

    private fun route(id: Long) = RouteItem(id, 10, "A", "V1", 1, "V1", "#fff", "", "red")

    private fun catalog() = RouteCatalog(1, null, key.queryDate.toString(), emptyList(), emptyList(), emptyList())
}

private class FakeRemote(
    private val keyResponses: MutableList<FilteringKeyResponse>,
    private val pages: MutableList<RoutePageResponse> = mutableListOf(),
) : RouteCatalogRemoteDataSource {
    var keyCalls = 0
    val requestEtags = mutableListOf<String?>()
    val pageRequests = mutableListOf<Pair<Int, Int>>()

    override suspend fun filteringKey(
        key: RouteCatalogKey,
        etag: String?,
    ): FilteringKeyResponse {
        keyCalls++
        requestEtags += etag
        return keyResponses.removeFirst()
    }

    override suspend fun routePage(
        key: RouteCatalogKey,
        floor: Int,
        page: Int,
    ): RoutePageResponse {
        pageRequests += floor to page
        return pages.removeFirst()
    }
}

private class BlockingRemote(
    private val gate: CompletableDeferred<Unit>,
) : RouteCatalogRemoteDataSource {
    val started = CompletableDeferred<Unit>()
    val keyCalls = AtomicInteger()
    val pageCalls = AtomicInteger()

    override suspend fun filteringKey(
        key: RouteCatalogKey,
        etag: String?,
    ): FilteringKeyResponse {
        val call = keyCalls.incrementAndGet()
        return if (call == 1) {
            started.complete(Unit)
            gate.await()
            FilteringKeyResponse.Modified(
                GetGymFilteringKeyResponse(
                    emptyList(),
                    1,
                    1,
                    sectorList = emptyList(),
                    timePoint = key.queryDate.toString(),
                ),
                "v1",
                "max-age=15",
            )
        } else {
            FilteringKeyResponse.NotModified("max-age=15")
        }
    }

    override suspend fun routePage(
        key: RouteCatalogKey,
        floor: Int,
        page: Int,
    ): RoutePageResponse {
        pageCalls.incrementAndGet()
        return RoutePageResponse.Success(0, false, emptyList())
    }
}

private data class StoreFailures(
    val metadata: Throwable? = null,
    val observe: Throwable? = null,
    val touch: Throwable? = null,
)

private class FakeStore(
    metadata: StoredCatalogMetadata? = null,
    private val entries: MutableList<CacheEntry> = mutableListOf(),
    private val replaceFailure: Throwable? = null,
    catalog: RouteCatalog? = null,
    private val freshKeys: Set<RouteCatalogKey> = emptySet(),
    private val failures: StoreFailures = StoreFailures(),
) : RouteCatalogStore {
    private val stored = MutableStateFlow(StoredCatalog(catalog, metadata))
    var lastWrite: CatalogWrite? = null
    var replaceCount = 0
    var lastAccessedAtEpochMs: Long? = null
    val deleted = mutableListOf<RouteCatalogKey>()

    override fun observe(key: RouteCatalogKey): Flow<StoredCatalog> {
        failures.observe?.let { throw it }
        return stored
    }

    override suspend fun metadata(key: RouteCatalogKey): StoredCatalogMetadata? {
        failures.metadata?.let { throw it }
        return if (key in freshKeys) StoredCatalogMetadata("fresh", 0L, 30_000L) else stored.value.metadata
    }

    override suspend fun replace(write: CatalogWrite) {
        replaceFailure?.let { throw it }
        replaceCount++
        lastWrite = write
        stored.value = StoredCatalog(
            write.catalog,
            StoredCatalogMetadata(write.etag, write.receivedAtEpochMs, write.freshUntilEpochMs),
        )
        entries += CacheEntry(write.key, write.estimatedBytes, write.receivedAtEpochMs)
    }

    override suspend fun touchFreshness(
        key: RouteCatalogKey,
        receivedAtEpochMs: Long,
        freshUntilEpochMs: Long,
    ) {
        stored.value = stored.value.copy(
            metadata = stored.value.metadata?.copy(
                receivedAtEpochMs = receivedAtEpochMs,
                freshUntilEpochMs = freshUntilEpochMs,
            ),
        )
    }

    override suspend fun touchAccess(
        key: RouteCatalogKey,
        accessedAtEpochMs: Long,
    ) {
        failures.touch?.let { throw it }
        lastAccessedAtEpochMs = accessedAtEpochMs
    }

    override suspend fun entries(): List<CacheEntry> = entries

    override suspend fun delete(key: RouteCatalogKey) {
        deleted += key
        entries.removeAll { it.key == key }
    }
}
