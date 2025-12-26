package com.climus.climeet.data.repository.route

import kotlinx.coroutines.flow.Flow

interface RouteCatalogRepository {
    /**
     * 암장·날짜의 전체 카탈로그를 전달합니다. 구독을 시작할 때마다 필요한 갱신을 한 번 시도하고,
     * 구독 중에는 해당 키를 제거 대상에서 보호합니다. 재시도는 다시 구독하면 됩니다.
     */
    fun observe(key: RouteCatalogKey): Flow<RouteCatalogState>
}
