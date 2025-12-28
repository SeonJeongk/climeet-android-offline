package com.climus.climeet.presentation.ui.main.global.selectsector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.climus.climeet.data.repository.route.RouteCatalog
import com.climus.climeet.data.repository.route.RouteCatalogKey
import com.climus.climeet.data.repository.route.RouteCatalogRepository
import com.climus.climeet.data.repository.route.RouteCatalogState
import com.climus.climeet.presentation.ui.main.global.RouteFilter
import com.climus.climeet.presentation.ui.main.global.keptIn
import com.climus.climeet.presentation.ui.main.global.selectsector.model.GymLevelUiData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.RouteUiData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.SectorNameUiData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.SelectedFilter
import com.climus.climeet.presentation.ui.main.global.toRouteCatalogUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SelectSectorBottomSheetUiState(
    val isSingleFloor: Boolean = false,
    val curFloor: Int = 0,
    val layoutImg: String? = "",
    val firstFloorBtnState: FloorBtnState = FloorBtnState.FloorSelected,
    val secondFloorBtnState: FloorBtnState = FloorBtnState.FloorUnSelected,
    val backgroundImage: String = "",
    val sectorNameList: List<SectorNameUiData> = emptyList(),
    val gymLevelList: List<GymLevelUiData> = emptyList(),
    val routeList: List<RouteUiData> = emptyList(),
    val selectedSector: SectorNameUiData = SectorNameUiData {},
    val selectedLevel: GymLevelUiData = GymLevelUiData {},
    val selectedRoute: RouteUiData = RouteUiData {},
    val selectedFilter: SelectedFilter = SelectedFilter(),
)

sealed class FloorBtnState {
    data object FloorSelected : FloorBtnState()

    data object FloorUnSelected : FloorBtnState()
}

sealed class SelectSectorBottomSheetEvent {
    data object NavigateToBack : SelectSectorBottomSheetEvent()

    data class ApplyFilter(
        val filter: SelectedFilter,
    ) : SelectSectorBottomSheetEvent()

    data object DismissDialog : SelectSectorBottomSheetEvent()

    data class ShowToastMessage(
        val msg: String,
    ) : SelectSectorBottomSheetEvent()
}

@HiltViewModel
class SelectSectorBottomSheetViewModel @Inject constructor(
    private val repository: RouteCatalogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SelectSectorBottomSheetUiState())
    val uiState: StateFlow<SelectSectorBottomSheetUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<SelectSectorBottomSheetEvent>()
    val event: SharedFlow<SelectSectorBottomSheetEvent> = _event.asSharedFlow()

    private var sectorNameList = listOf<SectorNameUiData>()
    private var gymLevelList = listOf<GymLevelUiData>()

    var cragId: Long = 0
    var cragName: String = ""
    private var catalogJob: Job? = null
    private var catalog: RouteCatalog? = null
    private var routeFilter = RouteFilter(1)

    fun setCragInfo(
        id: Long,
        name: String,
    ) {
        cragId = id
        cragName = name
        getCragInfo(cragId)
    }

    fun navigateToBack() {
        viewModelScope.launch {
            _event.emit(SelectSectorBottomSheetEvent.NavigateToBack)
        }
    }

    private fun getCragInfo(id: Long) {
        if (id <= 0L) return
        observeCatalog(1, restart = true)
    }

    fun selectFloor(floor: Int) {
        _uiState.update { state ->
            state.copy(
                secondFloorBtnState = if (floor ==
                    2
                ) {
                    FloorBtnState.FloorSelected
                } else {
                    FloorBtnState.FloorUnSelected
                },
                firstFloorBtnState = if (floor == 1) FloorBtnState.FloorSelected else FloorBtnState.FloorUnSelected,
                curFloor = floor,
                sectorNameList = sectorNameList.filter {
                    it.floor == floor
                },
                selectedRoute = RouteUiData {},
            )
        }
        setFloorInfo(floor)
    }

    private fun setFloorInfo(floor: Int) {
        observeCatalog(floor)
    }

    private fun getRouteList(
        floor: Int,
        sectorId: Long? = null,
        difficulty: Int? = null,
    ) {
        observeCatalog(floor, sectorId, difficulty)
    }

    private fun observeCatalog(
        floor: Int,
        sectorId: Long? = null,
        difficulty: Int? = null,
        restart: Boolean = false,
    ) {
        routeFilter = RouteFilter(floor, sectorId, difficulty)
        if (!restart) {
            renderCatalog()
            return
        }
        val key = RouteCatalogKey(cragId, LocalDate.now())
        catalog = null
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            repository.observe(key).collect { state ->
                when (state) {
                    RouteCatalogState.Loading -> Unit
                    is RouteCatalogState.Failed -> {
                        _event.emit(SelectSectorBottomSheetEvent.ShowToastMessage(state.error.msg))
                    }

                    is RouteCatalogState.Content -> {
                        catalog = state.catalog
                        renderCatalog()
                    }
                }
            }
        }
    }

    private fun renderCatalog() {
        val current = catalog ?: return
        val catalog = current.toRouteCatalogUi(routeFilter, ::selectSectorName, ::selectGymLevel, ::selectRoute)
        sectorNameList = catalog.sectors
        gymLevelList = catalog.levels
        _uiState.update { uiState ->
            uiState.copy(
                isSingleFloor = catalog.isSingleFloor,
                layoutImg = catalog.layoutImg,
                curFloor = catalog.floor,
                sectorNameList = catalog.floorSectors,
                gymLevelList = catalog.selectedLevels,
                routeList = catalog.routes,
                selectedRoute = uiState.selectedRoute.keptIn(catalog.routes),
            )
        }
    }

    private fun selectSectorName(item: SectorNameUiData) {
        _uiState.update { state ->
            state.copy(
                sectorNameList = state.sectorNameList.map {
                    it.copy(isSelected = it.sectorId == item.sectorId)
                },
                selectedSector = item,
                selectedRoute = RouteUiData {},
            )
        }

        getRouteList(
            uiState.value.curFloor,
            item.sectorId,
            if (uiState.value.selectedLevel.difficulty != -1) uiState.value.selectedLevel.difficulty else null,
        )
    }

    private fun selectGymLevel(item: GymLevelUiData) {
        _uiState.update { state ->
            state.copy(
                gymLevelList = state.gymLevelList.map {
                    it.copy(isSelected = it.difficulty == item.difficulty)
                },
                selectedLevel = item,
                selectedRoute = RouteUiData {},
            )
        }

        getRouteList(
            uiState.value.curFloor,
            if (uiState.value.selectedSector.sectorId != -1L) uiState.value.selectedSector.sectorId else null,
            item.difficulty,
        )
    }

    private fun selectRoute(item: RouteUiData) {
        _uiState.update { state ->
            state.copy(selectedRoute = item)
        }
    }

    fun applySectorFilter() {
        val selectedFilter = SelectedFilter(
            cragId = cragId,
            routeId = uiState.value.selectedRoute.routeId,
            sectorId = uiState.value.selectedSector.sectorId,
            difficulty = uiState.value.selectedLevel.difficulty,
            sectorName = uiState.value.selectedRoute.sectorName.ifBlank { uiState.value.selectedSector.name },
            cragName = cragName,
            gymLevelName = uiState.value.selectedRoute.gymLevelName.ifBlank { uiState.value.selectedLevel.levelName },
        )
        viewModelScope.launch {
            _event.emit(
                SelectSectorBottomSheetEvent.ApplyFilter(selectedFilter),
            )
        }
    }

    fun dismissDialog() {
        viewModelScope.launch {
            _event.emit(SelectSectorBottomSheetEvent.DismissDialog)
        }
    }
}
