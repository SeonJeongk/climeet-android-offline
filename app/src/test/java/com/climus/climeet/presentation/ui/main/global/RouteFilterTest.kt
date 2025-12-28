package com.climus.climeet.presentation.ui.main.global

import com.climus.climeet.data.repository.route.CachedRoute
import com.climus.climeet.data.repository.route.RouteCatalog
import com.climus.climeet.data.repository.route.RouteDifficulty
import com.climus.climeet.data.repository.route.RouteSector
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteFilterTest {
    private val catalog =
        RouteCatalog(
            maxFloor = 2,
            layoutImageUrl = null,
            serverTimePoint = "2026-08-12",
            sectors =
                listOf(
                    RouteSector(101, 1, "1A", ""),
                    RouteSector(102, 1, "1B", ""),
                    RouteSector(201, 2, "2A", ""),
                ),
            difficulties = listOf(RouteDifficulty(1, "V1", "V1", "#fff"), RouteDifficulty(2, "V2", "V2", "#fff")),
            routes = listOf(route(1, 101, 1), route(2, 102, 2), route(3, 201, 1), route(4, 101, 2)),
        )

    @Test
    fun floorOnly_filterRoutes_keepsRoutesOnThatFloor() {
        assertEquals(listOf(1L, 2L, 4L), catalog.filterRoutes(RouteFilter(1)).map { it.routeId })
        assertEquals(listOf(3L), catalog.filterRoutes(RouteFilter(2)).map { it.routeId })
    }

    @Test
    fun sectorAndDifficulty_filterRoutes_appliesBoth() {
        assertEquals(listOf(4L), catalog.filterRoutes(RouteFilter(1, 101, 2)).map { it.routeId })
    }

    @Test
    fun sectorOnOtherFloor_filterRoutes_returnsEmpty() {
        assertEquals(emptyList<Long>(), catalog.filterRoutes(RouteFilter(2, 101)).map { it.routeId })
    }

    private fun route(
        id: Long,
        sectorId: Long,
        difficulty: Int,
    ) = CachedRoute(id, sectorId, "", "V$difficulty", difficulty, "V$difficulty", "#fff", "", "하양")
}
