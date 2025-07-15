package com.climus.climeet.presentation.ui.main.global.gymprofile.route

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.model.ErrorType
import com.climus.climeet.data.model.request.GetGymRouteInfoRequest
import com.climus.climeet.data.model.response.GetGymFilteringKeyResponse
import com.climus.climeet.data.repository.MainRepository
import com.climus.climeet.presentation.ui.main.global.gymprofile.GymProfileData
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
    private val repository: MainRepository,
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
    private var filteringKey: GetGymFilteringKeyResponse? = null
    private var loadJob: Job? = null
    private var generation = 0L

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
        val sector = filteringKey?.sectorList?.find { it.sectorId == current.sectorId && it.floor == floor }
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
            filteringKey = null
            _isDataLoaded.value = false
            _uiState.value = GymProfileSelectSectorUiState(curFloor = 1)
        }
        updateSelection(next)
        startLoad(next)
    }

    private fun updateSelection(snapshot: RouteQuery) {
        val sectors = filteringKey?.sectorList.orEmpty()
            .filter { it.floor == snapshot.floor }
            .map { it.toSectorNameUiData(::selectSectorName).copy(isSelected = it.sectorId == snapshot.sectorId) }
        val levels = filteringKey?.difficultyList.orEmpty()
            .map {
                it.toGymLevelUiData(::selectGymLevel).copy(
                    levelName = if (it.gymDifficultyName == "V0") "VB" else it.gymDifficultyName,
                    isSelected = it.difficulty == snapshot.difficulty,
                )
            }
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
                routeList = emptyList(),
                selectedRoute = RouteUiData {},
            )
        }
    }

    private fun startLoad(snapshot: RouteQuery) {
        // 취소가 이전 작업을 즉시 재개하더라도 새 요청의 상태를 변경하지 못하게 합니다.
        val requestGeneration = ++generation
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                routeList = emptyList(),
                selectedRoute = RouteUiData {},
                loadState = if (snapshot.gymId > 0) RouteLoadState.Loading else RouteLoadState.Idle,
            )
        }
        if (snapshot.gymId <= 0) return
        loadJob = viewModelScope.launch {
            if (filteringKey == null) {
                when (val result = repository.getGymFilteringKeyTime(snapshot.gymId, snapshot.timePoint)) {
                    is BaseState.Success -> {
                        if (requestGeneration != generation) return@launch
                        filteringKey = result.body
                        _isDataLoaded.value = true
                        _uiState.update {
                            it.copy(isSingleFloor = result.body.maxFloor == 1, layoutImg = result.body.layoutImageUrl)
                        }
                        updateSelection(snapshot)
                    }
                    is BaseState.Error -> {
                        publishError(result, requestGeneration)
                        return@launch
                    }
                }
            }
            loadPages(snapshot, requestGeneration)
        }
    }

    private suspend fun loadPages(
        snapshot: RouteQuery,
        requestGeneration: Long,
    ) {
        var page = 0
        val routes = mutableListOf<RouteUiData>()
        val identities = mutableSetOf<Long>()
        do {
            val request = GetGymRouteInfoRequest(
                page = page,
                size = 10,
                floor = snapshot.floor,
                sectorId = snapshot.sectorId,
                difficulty = snapshot.difficulty,
                timePoint = snapshot.timePoint,
            )
            val result = repository.getGymRouteInfoList(snapshot.gymId, request)
            if (requestGeneration != generation) return
            when (result) {
                is BaseState.Error -> {
                    publishError(result, requestGeneration)
                    return
                }
                is BaseState.Success -> {
                    if (result.body.result.any { !identities.add(it.routeId) }) {
                        publishError(
                            BaseState.Error(
                                "루트 응답에 중복된 ID가 있습니다",
                                "DUPLICATE_ROUTE_ID",
                                type = ErrorType.CONTRACT,
                            ),
                            requestGeneration,
                        )
                        return
                    }
                    routes += result.body.result.map {
                        it.toRouteUiData(::selectRoute).copy(
                            gymLevelName = if (it.gymDifficultyName == "V0") "VB" else it.gymDifficultyName,
                            climeetLevelName = if (it.climeetDifficultyName == "V0") "VB" else it.climeetDifficultyName,
                        )
                    }
                    if (!result.body.hasNext) break
                    page++
                }
            }
        } while (true)
        if (requestGeneration != generation) return
        // 모든 페이지가 성공한 현재 요청만 한 번에 화면에 반영합니다.
        _uiState.update {
            it.copy(
                routeList = routes,
                loadState = if (routes.isEmpty()) RouteLoadState.Empty else RouteLoadState.Content,
            )
        }
    }

    private suspend fun publishError(
        error: BaseState.Error,
        requestGeneration: Long,
    ) {
        if (requestGeneration != generation) return
        _uiState.update {
            it.copy(
                routeList = emptyList(),
                loadState = if (error.retryable) {
                    RouteLoadState.RetryableError(
                        error,
                    )
                } else {
                    RouteLoadState.TerminalError(error)
                },
            )
        }
        _event.emit(GymProfileRouteEvent.ShowToastMessage(error.msg))
    }

    fun retry() {
        if (uiState.value.loadState !is RouteLoadState.RetryableError) return
        query?.let(::startLoad)
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
