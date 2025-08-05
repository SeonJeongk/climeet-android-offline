package com.climus.climeet.presentation.ui.main.record.session.record

import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.model.request.GetGymRouteInfoRequest
import com.climus.climeet.data.model.response.RouteItem
import com.climus.climeet.data.repository.MainRepository

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
