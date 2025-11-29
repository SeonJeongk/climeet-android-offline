package com.climus.climeet.presentation.ui.main.global.gymprofile.route

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.repository.route.Freshness
import com.climus.climeet.data.repository.route.RouteCatalog
import com.climus.climeet.data.repository.route.RouteCatalogKey
import com.climus.climeet.data.repository.route.RouteCatalogRepository
import com.climus.climeet.data.repository.route.RouteCatalogState
import com.climus.climeet.data.repository.route.RouteFilter
import com.climus.climeet.presentation.ui.main.global.gymprofile.GymProfileData
import com.climus.climeet.presentation.ui.main.global.keptIn
import com.climus.climeet.presentation.ui.main.global.selectsector.FloorBtnState
import com.climus.climeet.presentation.ui.main.global.selectsector.model.GymLevelUiData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.RouteUiData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.SectorNameUiData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.SelectedFilter
import com.climus.climeet.presentation.ui.main.global.toGymLevelUiData
import com.climus.climeet.presentation.ui.main.global.toRouteUiData
import com.climus.climeet.presentation.ui.main.global.toSectorNameUiData
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

data class RouteQuery(
    val gymId: Long,
    val timePoint: String,
    val floor: Int,
    val sectorId: Long?,
    val difficulty: Int?,
)

sealed class RouteLoadState {
    data object Idle : RouteLoadState()

    data object Loading : RouteLoadState()

    data object Content : RouteLoadState()

    data object Empty : RouteLoadState()

    data class RetryableError(
        val error: BaseState.Error,
    ) : RouteLoadState()

    data class TerminalError(
        val error: BaseState.Error,
    ) : RouteLoadState()
}

data class GymProfileSelectSectorUiState(
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
    val loadState: RouteLoadState = RouteLoadState.Idle,
    val cacheFreshness: Freshness = Freshness.ABSENT,
    val cacheReceivedAtEpochMs: Long? = null,
    val isRefreshing: Boolean = false,
)

sealed class GymProfileRouteEvent {
    data object ShowDatePicker : GymProfileRouteEvent()

    data object DeleteFilter : GymProfileRouteEvent()

    data class ApplyFilter(
        val filter: SelectedFilter,
    ) : GymProfileRouteEvent()

    data class ShowToastMessage(
        val msg: String,
    ) : GymProfileRouteEvent()
}

@HiltViewModel
class GymProfileRouteViewModel @Inject constructor(
    private val repository: RouteCatalogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GymProfileSelectSectorUiState())
    val uiState: StateFlow<GymProfileSelectSectorUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<GymProfileRouteEvent>()
    val event: SharedFlow<GymProfileRouteEvent> = _event.asSharedFlow()

    private val _isDataLoaded = MutableStateFlow(false)
    val isDataLoaded: StateFlow<Boolean> = _isDataLoaded.asStateFlow()

    private val initDate = LocalDate.now()
    val datePickText = MutableStateFlow("${initDate.year}년 ${initDate.monthValue}월 ${initDate.dayOfMonth}일")
    val selectedDate = MutableLiveData(initDate)
    private var timePoint = initDate.toString()

    var cragId: Long = 0
        private set
    var cragName: String = ""
        private set

    private var query: RouteQuery? = null
    private var catalog: RouteCatalog? = null
    private var loadJob: Job? = null
    private val routeFilter = MutableStateFlow(RouteFilter(1))

    init {
        GymProfileData.setSelectedDate(initDate)
    }

    fun setSelectedDate(date: LocalDate) {
        selectedDate.value = date
        setDate()
    }

    fun setCragInfo(
        id: Long,
        name: String,
    ) {
        cragId = id
        cragName = name
        changeQuery(RouteQuery(id, timePoint, 1, null, null), resetFilters = true)
    }

    fun setDate() {
        val date = selectedDate.value ?: return
        if (date.toString() == timePoint) return
        timePoint = date.toString()
        datePickText.value = "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일"
        GymProfileData.setSelectedDate(date)
        changeQuery(RouteQuery(cragId, timePoint, 1, null, null), resetFilters = true)
    }

    fun selectFloor(floor: Int) {
        val current = query ?: return
        val sector = catalog?.sectors?.find { it.sectorId == current.sectorId && it.floor == floor }
        changeQuery(current.copy(floor = floor, sectorId = sector?.sectorId))
    }

    private fun selectSectorName(item: SectorNameUiData) {
        val current = query ?: return
        if (uiState.value.sectorNameList.none { it.sectorId == item.sectorId }) return
        changeQuery(current.copy(sectorId = item.sectorId))
    }

    private fun selectGymLevel(item: GymLevelUiData) {
        val current = query ?: return
        if (uiState.value.gymLevelList.none { it.difficulty == item.difficulty }) return
        changeQuery(current.copy(difficulty = item.difficulty))
    }

    private fun changeQuery(
        next: RouteQuery,
        resetFilters: Boolean = false,
    ) {
        if (query == next) return
        // 같은 암장의 observer 재전달은 사용자가 고른 층과 필터를 초기화하지 않습니다.
        if (resetFilters && query?.gymId == next.gymId && query?.timePoint == next.timePoint) return
        query = next
        if (resetFilters) {
            catalog = null
            _isDataLoaded.value = false
            _uiState.value = GymProfileSelectSectorUiState(curFloor = 1)
        }
        updateSelection(next)
        startLoad(next, restart = resetFilters)
    }

    private fun updateSelection(snapshot: RouteQuery) {
        val sectors = catalog?.sectors.orEmpty()
            .filter { it.floor == snapshot.floor }
            .map { it.toSectorNameUiData(::selectSectorName).copy(isSelected = it.sectorId == snapshot.sectorId) }
        val levels = catalog?.difficulties.orEmpty()
            .map { it.toGymLevelUiData(::selectGymLevel).copy(isSelected = it.difficulty == snapshot.difficulty) }
        _uiState.update {
            it.copy(
                curFloor = snapshot.floor,
                firstFloorBtnState = if (snapshot.floor ==
                    1
                ) {
                    FloorBtnState.FloorSelected
                } else {
                    FloorBtnState.FloorUnSelected
                },
                secondFloorBtnState = if (snapshot.floor ==
                    2
                ) {
                    FloorBtnState.FloorSelected
                } else {
                    FloorBtnState.FloorUnSelected
                },
                sectorNameList = sectors,
                gymLevelList = levels,
                selectedSector = sectors.find { sector -> sector.isSelected } ?: SectorNameUiData {},
                selectedLevel = levels.find { level -> level.isSelected } ?: GymLevelUiData {},
            )
        }
    }

    private fun startLoad(
        snapshot: RouteQuery,
        restart: Boolean = false,
    ) {
        // 빠른 필터 전환으로 이전 값에 돌아오면 새 결과가 오지 않으므로, 같은 키의 목록은 결과가 올 때까지 유지합니다.
        _uiState.update {
            it.copy(
                routeList = if (restart) emptyList() else it.routeList,
                selectedRoute = RouteUiData {},
                loadState = when {
                    snapshot.gymId <= 0 -> RouteLoadState.Idle
                    restart -> RouteLoadState.Loading
                    else -> it.loadState
                },
            )
        }
        if (snapshot.gymId <= 0) return
        routeFilter.value = RouteFilter(snapshot.floor, snapshot.sectorId, snapshot.difficulty)
        if (!restart) return
        val key = RouteCatalogKey(snapshot.gymId, LocalDate.parse(snapshot.timePoint))
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            repository.observe(key, routeFilter).collect(::render)
        }
    }

    private suspend fun render(state: RouteCatalogState) {
        when (state) {
            RouteCatalogState.Loading -> Unit
            is RouteCatalogState.Failed -> {
                _uiState.update {
                    it.copy(
                        loadState = if (state.error.retryable) {
                            RouteLoadState.RetryableError(state.error)
                        } else {
                            RouteLoadState.TerminalError(state.error)
                        },
                    )
                }
                _event.emit(GymProfileRouteEvent.ShowToastMessage(state.error.msg))
            }

            is RouteCatalogState.Content -> renderContent(state)
        }
    }

    private fun renderContent(state: RouteCatalogState.Content) {
        val snapshot = query ?: return
        if (state.filter != routeFilter.value) return
        catalog = state.catalog
        _isDataLoaded.value = true
        _uiState.update {
            it.copy(
                isSingleFloor = state.catalog.maxFloor == 1,
                layoutImg = state.catalog.layoutImageUrl,
                cacheFreshness = state.freshness,
                cacheReceivedAtEpochMs = state.receivedAtEpochMs,
                isRefreshing = state.isRefreshing,
            )
        }
        updateSelection(snapshot)
        val routes = state.catalog.routes.map { it.toRouteUiData(::selectRoute) }
        _uiState.update {
            it.copy(
                routeList = routes,
                selectedRoute = it.selectedRoute.keptIn(routes),
                loadState = if (routes.isEmpty()) RouteLoadState.Empty else RouteLoadState.Content,
            )
        }
    }

    fun retry() {
        if (uiState.value.loadState !is RouteLoadState.RetryableError) return
        query?.let { startLoad(it, restart = true) }
    }

    private fun selectRoute(item: RouteUiData) {
        if (uiState.value.routeList.none { it.routeId == item.routeId }) return
        _uiState.update { it.copy(selectedRoute = item) }
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
            _event.emit(GymProfileRouteEvent.ApplyFilter(selectedFilter))
        }
    }

    fun clearSelectedRoute() {
        _uiState.update { it.copy(selectedRoute = RouteUiData {}) }
        viewModelScope.launch {
            _event.emit(GymProfileRouteEvent.DeleteFilter)
        }
    }

    fun showDatePicker() {
        viewModelScope.launch {
            _event.emit(GymProfileRouteEvent.ShowDatePicker)
        }
    }
}
