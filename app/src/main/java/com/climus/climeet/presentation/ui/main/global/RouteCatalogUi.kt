package com.climus.climeet.presentation.ui.main.global

import com.climus.climeet.data.repository.route.RouteCatalog
import com.climus.climeet.presentation.ui.main.global.selectsector.model.GymLevelUiData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.RouteUiData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.SectorNameUiData

/** [sectors]·[levels]는 선택 표시가 없는 전체 목록이고, [floorSectors]·[selectedLevels]는 현재 필터를 반영한 화면 목록입니다. */
data class RouteCatalogUi(
    val isSingleFloor: Boolean,
    val layoutImg: String?,
    val floor: Int,
    val sectors: List<SectorNameUiData>,
    val levels: List<GymLevelUiData>,
    val floorSectors: List<SectorNameUiData>,
    val selectedLevels: List<GymLevelUiData>,
    val routes: List<RouteUiData>,
)

fun RouteCatalog.toRouteCatalogUi(
    filter: RouteFilter,
    onSectorClick: (SectorNameUiData) -> Unit,
    onLevelClick: (GymLevelUiData) -> Unit,
    onRouteClick: (RouteUiData) -> Unit,
): RouteCatalogUi {
    val sectorItems = sectors.map { it.toSectorNameUiData(onSectorClick) }
    val levelItems = difficulties.map { it.toGymLevelUiData(onLevelClick) }
    return RouteCatalogUi(
        isSingleFloor = maxFloor == 1,
        layoutImg = layoutImageUrl,
        floor = filter.floor,
        sectors = sectorItems,
        levels = levelItems,
        floorSectors =
            sectorItems
                .filter { it.floor == filter.floor }
                .map { it.copy(isSelected = it.sectorId == filter.sectorId) },
        selectedLevels = levelItems.map { it.copy(isSelected = it.difficulty == filter.difficulty) },
        routes = filterRoutes(filter).map { it.toRouteUiData(onRouteClick) },
    )
}
