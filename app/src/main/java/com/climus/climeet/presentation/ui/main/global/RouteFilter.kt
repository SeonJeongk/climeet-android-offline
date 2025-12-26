package com.climus.climeet.presentation.ui.main.global

import com.climus.climeet.data.repository.route.CachedRoute
import com.climus.climeet.data.repository.route.RouteCatalog

data class RouteFilter(
    val floor: Int,
    val sectorId: Long? = null,
    val difficulty: Int? = null,
)

fun RouteCatalog.filterRoutes(filter: RouteFilter): List<CachedRoute> {
    val floorBySector = sectors.associate { it.sectorId to it.floor }
    return routes.filter { route ->
        floorBySector[route.sectorId] == filter.floor &&
            (filter.sectorId == null || route.sectorId == filter.sectorId) &&
            (filter.difficulty == null || route.difficulty == filter.difficulty)
    }
}
