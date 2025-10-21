package com.climus.climeet.data.repository.route

import com.climus.climeet.data.model.BaseState
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class RouteCatalogKey(
    val gymId: Long,
    val queryDate: LocalDate,
)

data class RouteFilter(
    val floor: Int,
    val sectorId: Long? = null,
    val difficulty: Int? = null,
)

enum class Freshness {
    FRESH,
    STALE,
    ABSENT,
}

data class RouteCatalog(
    val maxFloor: Int,
    val layoutImageUrl: String?,
    val serverTimePoint: String,
    val sectors: List<RouteSector>,
    val difficulties: List<RouteDifficulty>,
    val routes: List<CachedRoute>,
)

data class RouteSector(
    val sectorId: Long,
    val floor: Int,
    val name: String,
    val sectorImageUrl: String,
)

data class RouteDifficulty(
    val difficulty: Int,
    val climeetDifficultyName: String,
    val gymDifficultyName: String,
    val gymDifficultyColor: String,
)

data class CachedRoute(
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

/** 화면이 그릴 상태입니다. 데이터가 있으면 갱신 실패와 무관하게 [Content]이고, 데이터가 없을 때만 [Failed]입니다. */
sealed interface RouteCatalogState {
    data object Loading : RouteCatalogState

    data class Content(
        val catalog: RouteCatalog,
        val filter: RouteFilter,
        val freshness: Freshness,
        val isRefreshing: Boolean,
        val receivedAtEpochMs: Long?,
    ) : RouteCatalogState

    data class Failed(
        val error: BaseState.Error,
    ) : RouteCatalogState
}

sealed interface RefreshResult {
    data object SkippedFresh : RefreshResult

    data object NotModified : RefreshResult

    data object Updated : RefreshResult

    data class KeptStale(
        val error: BaseState.Error,
    ) : RefreshResult

    data class FailedNoData(
        val error: BaseState.Error,
    ) : RefreshResult
}

interface RouteCatalogRepository {
    /**
     * 구독을 시작할 때마다 필요한 갱신을 한 번 시도하고, 구독 중에는 해당 키를 제거 대상에서 보호합니다.
     * 필터가 바뀌어도 네트워크를 다시 호출하지 않습니다. 재시도는 다시 구독하면 됩니다.
     */
    fun observe(
        key: RouteCatalogKey,
        filter: Flow<RouteFilter>,
    ): Flow<RouteCatalogState>
}

data class CacheEntry(
    val key: RouteCatalogKey,
    val estimatedBytes: Long,
    val lastAccessedAtEpochMs: Long,
)
