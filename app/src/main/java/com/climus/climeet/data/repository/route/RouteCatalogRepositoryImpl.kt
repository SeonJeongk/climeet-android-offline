package com.climus.climeet.data.repository.route

import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.model.ErrorType
import com.climus.climeet.data.model.response.GetGymFilteringKeyResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private data class RefreshState(
    val isRefreshing: Boolean = false,
    val error: BaseState.Error? = null,
)

@Singleton
class RouteCatalogRepositoryImpl @Inject constructor(
    private val remote: RouteCatalogRemoteDataSource,
    private val store: RouteCatalogStore,
    private val policy: RouteCachePolicy,
    private val clock: Clock,
    private val networkAvailability: NetworkAvailability,
) : RouteCatalogRepository {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = mutableMapOf<RouteCatalogKey, Deferred<RefreshResult>>()
    private val refreshStates = ConcurrentHashMap<RouteCatalogKey, MutableStateFlow<RefreshState>>()
    private val activeKeys = mutableMapOf<RouteCatalogKey, Int>()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(
        key: RouteCatalogKey,
        filter: Flow<RouteFilter>,
    ): Flow<RouteCatalogState> =
        filter
            .distinctUntilChanged()
            .flatMapLatest { currentFilter ->
                combine(store.observe(key, currentFilter), stateFor(key)) { stored, refresh ->
                    stored.toState(currentFilter, refresh)
                }
            }.onStart {
                retain(key)
                startRefresh(key)
                prepareCache(key)
            }.onCompletion { release(key) }
            .catch { error -> emit(RouteCatalogState.Failed(localStorageError(error))) }
            .distinctUntilChanged()

    internal suspend fun refresh(key: RouteCatalogKey): RefreshResult {
        val metadata = try {
            store.metadata(key)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return recordFailure(key, localStorageError(error), null)
        }
        if (metadata?.catalogComplete == true && policy.freshness(metadata.freshUntilEpochMs) == Freshness.FRESH) {
            return RefreshResult.SkippedFresh
        }
        if (networkAvailability.isDefinitelyOffline()) {
            return recordFailure(key, offlineError(), metadata?.takeIf { it.catalogComplete })
        }
        val deferred = synchronized(inFlight) {
            inFlight[key] ?: repositoryScope.async {
                performRefresh(key)
            }.also { created ->
                inFlight[key] = created
                created.invokeOnCompletion {
                    synchronized(inFlight) {
                        if (inFlight[key] === created) inFlight.remove(key)
                    }
                }
            }
        }
        return deferred.await()
    }

    private fun StoredCatalog.toState(
        filter: RouteFilter,
        refresh: RefreshState,
    ): RouteCatalogState {
        val currentCatalog = catalog
        val completeMetadata = metadata?.takeIf { it.catalogComplete }
        val error = refresh.error
        return when {
            currentCatalog != null -> RouteCatalogState.Content(
                catalog = currentCatalog,
                filter = filter,
                freshness = policy.freshness(completeMetadata?.freshUntilEpochMs),
                isRefreshing = refresh.isRefreshing,
                receivedAtEpochMs = completeMetadata?.receivedAtEpochMs,
            )

            error != null -> RouteCatalogState.Failed(error)
            else -> RouteCatalogState.Loading
        }
    }

    private fun startRefresh(key: RouteCatalogKey) {
        // 이전 시도의 오류가 새 구독의 첫 상태로 다시 전달되지 않게 지웁니다.
        synchronized(inFlight) {
            if (key !in inFlight) stateFor(key).value = RefreshState()
        }
        repositoryScope.launch { refresh(key) }
    }

    private suspend fun prepareCache(key: RouteCatalogKey) {
        try {
            store.touchAccess(key, clock.millis())
            trimToCapacity()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // 접근 시각과 용량 정리에 실패해도 저장된 목록은 읽을 수 있습니다.
        }
    }

    private fun retain(key: RouteCatalogKey) {
        synchronized(activeKeys) { activeKeys[key] = (activeKeys[key] ?: 0) + 1 }
    }

    private fun release(key: RouteCatalogKey) {
        synchronized(activeKeys) {
            val remaining = (activeKeys[key] ?: 0) - 1
            if (remaining > 0) activeKeys[key] = remaining else activeKeys.remove(key)
        }
    }

    private fun recordFailure(
        key: RouteCatalogKey,
        error: BaseState.Error,
        existing: StoredCatalogMetadata?,
    ): RefreshResult =
        failureResult(error, existing).also { result ->
            stateFor(key).value = result.toRefreshState()
        }

    private suspend fun performRefresh(key: RouteCatalogKey): RefreshResult {
        stateFor(key).value = RefreshState(isRefreshing = true)
        var terminalState = RefreshState()
        return try {
            val result = refreshWithVersionCheck(key)
            terminalState = result.toRefreshState()
            result
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val existing = try {
                store.metadata(key)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            failureResult(localStorageError(error), existing?.takeIf { it.catalogComplete }).also { result ->
                terminalState = result.toRefreshState()
            }
        } finally {
            stateFor(key).value = terminalState
        }
    }

    private suspend fun refreshWithVersionCheck(key: RouteCatalogKey): RefreshResult {
        val storedMetadata = store.metadata(key)
        val existing = storedMetadata?.takeIf { it.catalogComplete }
        val initial = remote.filteringKey(key, storedMetadata?.etag)
        when (initial) {
            is FilteringKeyResponse.Failed -> return failureResult(initial.error, existing)
            is FilteringKeyResponse.NotModified -> {
                if (storedMetadata == null) return failureResult(missingCatalogError(), null)
                if (!storedMetadata.catalogComplete) {
                    return recoverIncompleteCatalog(key)
                }
                val receivedAt = clock.millis()
                store.touchFreshness(key, receivedAt, policy.freshUntilEpochMs(initial.cacheControl, receivedAt))
                return RefreshResult.NotModified
            }

            is FilteringKeyResponse.Modified -> {
                return collectAndStore(key, initial, existing)
            }
        }
    }

    private suspend fun recoverIncompleteCatalog(key: RouteCatalogKey): RefreshResult =
        when (val recovery = remote.filteringKey(key, null)) {
            is FilteringKeyResponse.Failed -> failureResult(recovery.error, null)
            is FilteringKeyResponse.NotModified -> failureResult(incompleteCatalogError(), null)
            is FilteringKeyResponse.Modified -> collectAndStore(key, recovery, null)
        }

    private suspend fun collectAndStore(
        key: RouteCatalogKey,
        firstKeyResponse: FilteringKeyResponse.Modified,
        existing: StoredCatalogMetadata?,
    ): RefreshResult {
        var keyResponse = firstKeyResponse
        repeat(MAX_VERSION_ATTEMPTS) { attempt ->
            val contractError = validateFilteringKey(key, keyResponse)
            if (contractError != null) return failureResult(contractError, existing)
            val routes = mutableListOf<com.climus.climeet.data.model.response.RouteItem>()
            val routeIds = mutableSetOf<Long>()
            for (floor in 1..keyResponse.body.maxFloor) {
                var page = 0
                while (true) {
                    when (val pageResponse = remote.routePage(key, floor, page)) {
                        is RoutePageResponse.Failed -> return failureResult(pageResponse.error, existing)
                        is RoutePageResponse.Success -> {
                            if (pageResponse.page != page) {
                                return failureResult(pageContractError(page, pageResponse.page), existing)
                            }
                            if (pageResponse.routes.any { !routeIds.add(it.routeId) }) {
                                return failureResult(duplicateRouteError(), existing)
                            }
                            routes += pageResponse.routes
                            if (!pageResponse.hasNext) break
                            page++
                        }
                    }
                }
            }

            when (val validation = remote.filteringKey(key, keyResponse.etag)) {
                is FilteringKeyResponse.Failed -> return failureResult(validation.error, existing)
                is FilteringKeyResponse.NotModified -> {
                    writeCatalog(key, keyResponse, routes)
                    return RefreshResult.Updated
                }

                is FilteringKeyResponse.Modified -> {
                    if (validation.etag == keyResponse.etag) {
                        writeCatalog(key, validation, routes)
                        return RefreshResult.Updated
                    }
                    if (attempt == MAX_VERSION_ATTEMPTS - 1) {
                        return failureResult(versionChangedError(), existing)
                    }
                    keyResponse = validation
                }
            }
        }
        return failureResult(versionChangedError(), existing)
    }

    private suspend fun writeCatalog(
        key: RouteCatalogKey,
        keyResponse: FilteringKeyResponse.Modified,
        routeItems: List<com.climus.climeet.data.model.response.RouteItem>,
    ) {
        val body = keyResponse.body
        val catalog = RouteCatalog(
            maxFloor = body.maxFloor,
            layoutImageUrl = body.layoutImageUrl,
            serverTimePoint = body.timePoint,
            sectors = body.sectorList.map {
                RouteSector(it.sectorId, it.floor, it.name, it.sectorImageUrl)
            },
            difficulties = body.difficultyList.map {
                RouteDifficulty(
                    it.difficulty,
                    it.climeetDifficultyName,
                    it.gymDifficultyName,
                    it.gymDifficultyColor,
                )
            },
            routes = routeItems.map {
                CachedRoute(
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
        val receivedAt = clock.millis()
        store.replace(
            CatalogWrite(
                key = key,
                catalog = catalog,
                etag = keyResponse.etag,
                receivedAtEpochMs = receivedAt,
                freshUntilEpochMs = policy.freshUntilEpochMs(keyResponse.cacheControl, receivedAt),
                estimatedBytes = catalog.estimatedBytes(),
            ),
        )
        trimToCapacity()
    }

    private suspend fun trimToCapacity() {
        val protected = synchronized(activeKeys) { activeKeys.keys.toSet() }
        policy.selectEvictions(store.entries(), protected).forEach { store.delete(it) }
    }

    private fun validateFilteringKey(
        key: RouteCatalogKey,
        response: FilteringKeyResponse.Modified,
    ): BaseState.Error? =
        when {
            response.etag.isNullOrBlank() -> contractError("ETag가 없는 경로 카탈로그 응답입니다", "MISSING_ETAG")
            response.body.gymId.toLong() != key.gymId -> contractError("암장 ID가 요청과 다릅니다", "GYM_ID_MISMATCH")
            // 서버는 요청 날짜 기준으로 마지막에 등록한 카탈로그를 반환하므로 등록일이 더 이를 수 있습니다.
            !response.body.isRegisteredBy(key.queryDate) -> contractError("조회 날짜가 요청과 다릅니다", "DATE_MISMATCH")
            else -> null
        }

    private fun GetGymFilteringKeyResponse.isRegisteredBy(queryDate: LocalDate): Boolean =
        runCatching { !LocalDate.parse(timePoint).isAfter(queryDate) }.getOrDefault(false)

    private fun failureResult(
        error: BaseState.Error,
        existing: StoredCatalogMetadata?,
    ): RefreshResult = if (existing == null) RefreshResult.FailedNoData(error) else RefreshResult.KeptStale(error)

    private fun stateFor(key: RouteCatalogKey) = refreshStates.computeIfAbsent(key) { MutableStateFlow(RefreshState()) }

    private fun RefreshResult.toRefreshState() =
        RefreshState(
            error = when (this) {
                is RefreshResult.FailedNoData -> error
                is RefreshResult.KeptStale -> error
                else -> null
            },
        )

    private fun missingCatalogError() = contractError("저장된 목록 없이 304 응답을 받았습니다", "ORPHAN_304")

    private fun incompleteCatalogError() = contractError("저장된 경로 목록이 불완전합니다", "INCOMPLETE_CATALOG")

    private fun duplicateRouteError() = contractError("루트 응답에 중복된 ID가 있습니다", "DUPLICATE_ROUTE_ID")

    private fun pageContractError(
        expected: Int,
        actual: Int,
    ) = contractError("루트 페이지 번호가 다릅니다: expected=$expected actual=$actual", "PAGE_MISMATCH")

    private fun versionChangedError() = contractError("페이지 수집 중 카탈로그 버전이 계속 변경되었습니다", "CATALOG_VERSION_CHANGED")

    private fun offlineError() = BaseState.Error("네트워크에 연결되어 있지 않습니다", "OFFLINE", type = ErrorType.TRANSPORT)

    private fun localStorageError(error: Throwable) =
        BaseState.Error("저장된 경로를 갱신하지 못했습니다", "LOCAL_STORAGE", error, ErrorType.UNKNOWN)

    private fun contractError(
        message: String,
        code: String,
    ) = BaseState.Error(message, code, type = ErrorType.CONTRACT)

    private fun RouteCatalog.estimatedBytes(): Long {
        fun String?.bytes() = this?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L
        return 64L + layoutImageUrl.bytes() + serverTimePoint.bytes() +
            sectors.sumOf { 32L + it.name.bytes() + it.sectorImageUrl.bytes() } +
            difficulties.sumOf {
                24L + it.climeetDifficultyName.bytes() + it.gymDifficultyName.bytes() + it.gymDifficultyColor.bytes()
            } +
            routes.sumOf {
                64L + it.sectorName.bytes() + it.climeetDifficultyName.bytes() + it.gymDifficultyName.bytes() +
                    it.gymDifficultyColor.bytes() + it.routeImageUrl.bytes() + it.holdColor.bytes()
            }
    }

    private companion object {
        const val MAX_VERSION_ATTEMPTS = 2
    }
}
