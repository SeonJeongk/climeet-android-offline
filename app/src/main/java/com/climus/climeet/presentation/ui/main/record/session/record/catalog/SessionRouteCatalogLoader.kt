package com.climus.climeet.presentation.ui.main.record.session.record.catalog

import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.model.request.GetGymRouteInfoRequest
import com.climus.climeet.data.model.response.RouteItem
import com.climus.climeet.data.repository.MainRepository

/**
 * 선택한 암장의 route catalog를 마지막 페이지까지 불러옵니다.
 *
 * 중간 페이지에서 실패하면 일부 목록을 성공으로 반환하지 않고 해당 오류를 그대로 반환합니다.
 */
internal class SessionRouteCatalogLoader(
    private val repository: MainRepository,
) {
    suspend fun load(
        gymId: Long,
        floor: Int,
        sectorId: Long? = null,
        difficulty: Int? = null,
    ): BaseState<List<RouteItem>> {
        val routes = mutableListOf<RouteItem>()
        var page = 0
        do {
            when (
                val result = repository.getGymRouteInfoList(
                    gymId,
                    GetGymRouteInfoRequest(page, ROUTE_PAGE_SIZE, floor, sectorId, difficulty),
                )
            ) {
                is BaseState.Success -> {
                    routes += result.body.result
                    page += 1
                    if (!result.body.hasNext) return BaseState.Success(routes)
                }

                is BaseState.Error -> return result
            }
        } while (true)
    }

    private companion object {
        const val ROUTE_PAGE_SIZE = 10
    }
}
