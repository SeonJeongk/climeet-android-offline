package com.climus.climeet.presentation.ui.main.record.calendar.createclimbingrecord

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.model.request.ClimbingRecord
import com.climus.climeet.data.model.request.CreateClimbingRecordRequest
import com.climus.climeet.data.repository.MainRepository
import com.climus.climeet.data.repository.route.RouteCatalogKey
import com.climus.climeet.data.repository.route.RouteCatalogRepository
import com.climus.climeet.data.repository.route.RouteCatalogState
import com.climus.climeet.data.repository.route.RouteFilter
import com.climus.climeet.presentation.customview.DeleteDialog
import com.climus.climeet.presentation.ui.main.global.keptIn
import com.climus.climeet.presentation.ui.main.global.selectsector.FloorBtnState
import com.climus.climeet.presentation.ui.main.global.selectsector.model.GymLevelUiData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.RouteUiData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.SectorNameUiData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.SelectedFilter
import com.climus.climeet.presentation.ui.main.global.toGymLevelUiData
import com.climus.climeet.presentation.ui.main.global.toRouteUiData
import com.climus.climeet.presentation.ui.main.global.toSectorNameUiData
import com.climus.climeet.presentation.ui.main.record.model.CreateRecordData
import com.climus.climeet.presentation.util.formatRecordTimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class CreateClimbingRecordUiState(
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
    val clearBtnState: Boolean = false,
)

sealed class CreateClimbingRecordEvent {
    data object ShowDatePicker : CreateClimbingRecordEvent()

    data object ShowTimePicker : CreateClimbingRecordEvent()

    data object NavigateToSelectCrag : CreateClimbingRecordEvent()

    data object NavigateToBack : CreateClimbingRecordEvent()

    data object ClimbingComplete : CreateClimbingRecordEvent()

    data class ShowToastMessage(
        val msg: String,
    ) : CreateClimbingRecordEvent()
}

@HiltViewModel
class CreateClimbingRecordViewModel @Inject constructor(
    private val repository: MainRepository,
    private val routeCatalogRepository: RouteCatalogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateClimbingRecordUiState())
    val uiState: StateFlow<CreateClimbingRecordUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<CreateClimbingRecordEvent>()
    val event: SharedFlow<CreateClimbingRecordEvent> = _event.asSharedFlow()

    private val _items = MutableStateFlow<List<RouteUiData>>(emptyList())
    val items: StateFlow<List<RouteUiData>> = _items.asStateFlow()
    val isAcceptBtnEnabled: LiveData<Boolean> = _items.map { itemList ->
        itemList.isNotEmpty() && itemList.any { it.clearBtnState }
    }.asLiveData()

    val initDate = CreateRecordData.selectedDate
    val datePickText = MutableStateFlow(
        "${initDate.year}년 ${initDate.monthValue}월 ${initDate.dayOfMonth}일 (${initDate.dayOfWeek})",
    )
    val selectedDate = MutableStateFlow(initDate)

    val defaultTimeText = "시간을 입력해주세요 (선택)"
    val defaultTime: LocalTime = LocalTime.of(11, 0, 0)
    val timePickText = MutableStateFlow(defaultTimeText)
    val selectedStartTime = MutableStateFlow(defaultTime)
    val selectedEndTime = MutableStateFlow(defaultTime)

    private var sectorNameList = listOf<SectorNameUiData>()
    private var gymLevelList = listOf<GymLevelUiData>()

    var cragId: Long = 0
    var cragName: String = ""
    private var catalogJob: Job? = null
    private var catalogKey: RouteCatalogKey? = null
    private val routeFilter = MutableStateFlow(RouteFilter(1))

    val defaultCrag = Pair<Long, String>(0, "클라이밍 암장을 선택해주세요")
    val isSelectedCrag = MutableStateFlow(false)
    val selectedCragEvent = MutableStateFlow(defaultCrag)

    val challengeNumber = MutableStateFlow(0)

    val isToggleOn = MutableStateFlow(true)

    val alpha = MutableLiveData(1f)

    init {
        CreateRecordData.setSelectedTime(LocalTime.now(), defaultTime)
        selectCrag(0, "클라이밍 암장을 선택해주세요")
    }

    fun setSelectedDate(date: LocalDate) {
        selectedDate.value = date
        setDate()
    }

    fun setSelectedTime(
        start: LocalTime,
        end: LocalTime,
    ) {
        selectedStartTime.value = start
        selectedEndTime.value = end
        setTime()
    }

    fun showDatePicker() {
        viewModelScope.launch {
            _event.emit(CreateClimbingRecordEvent.ShowDatePicker)
        }
    }

    fun showTimePicker() {
        viewModelScope.launch {
            _event.emit(CreateClimbingRecordEvent.ShowTimePicker)
        }
    }

    fun setDate() {
        val date = selectedDate.value
        viewModelScope.launch(Dispatchers.Main) {
            val koreanDayOfWeek = when (date.dayOfWeek?.value) {
                1 -> "(월)"
                2 -> "(화)"
                3 -> "(수)"
                4 -> "(목)"
                5 -> "(금)"
                6 -> "(토)"
                7 -> "(일)"
                else -> throw IllegalArgumentException("Unsupported day of week: ${date.dayOfWeek}")
            }
            val year = date.year
            val month = date.month.value
            val day = date.dayOfMonth
            datePickText.value = "${year}년 ${month}월 ${day}일 $koreanDayOfWeek"
        }
        if (cragId > 0L) loadCatalog()
    }

    fun setTime() {
        val start = selectedStartTime.value
        val end = selectedEndTime.value

        timePickText.value = formatRecordTimeRange(start, end)
    }

    fun selectCrag(
        id: Long,
        name: String,
    ) {
        selectedCragEvent.value = Pair(id, name)
        cragId = id
        cragName = name
        getCragInfo(selectedCragEvent.value.first)
    }

    fun getCragInfo(id: Long) {
        if (id <= 0L) return
        loadCatalog()
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
                selectedSector = SectorNameUiData {},
                selectedLevel = GymLevelUiData {},
                selectedRoute = RouteUiData {},
            )
        }
        setFloorInfo(floor)
    }

    private fun setFloorInfo(floor: Int) {
        routeFilter.value = RouteFilter(floor)
    }

    private fun getRouteList(
        floor: Int,
        sectorId: Long? = null,
        difficulty: Int? = null,
    ) {
        routeFilter.value = RouteFilter(floor, sectorId, difficulty)
    }

    /** 같은 암장·날짜를 다시 고르면 현재 층과 필터를 유지한 채 다시 구독합니다. */
    private fun loadCatalog() {
        val key = RouteCatalogKey(cragId, selectedDate.value)
        if (key != catalogKey) clearCatalog()
        catalogKey = key
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            routeCatalogRepository.observe(key, routeFilter).collect { state ->
                when (state) {
                    RouteCatalogState.Loading -> Unit
                    is RouteCatalogState.Failed -> {
                        _event.emit(CreateClimbingRecordEvent.ShowToastMessage(state.error.msg))
                    }

                    is RouteCatalogState.Content -> renderCatalog(state)
                }
            }
        }
    }

    /** 다른 암장이나 날짜의 루트로 기록하지 않도록 이전 목록과 선택을 지웁니다. */
    private fun clearCatalog() {
        sectorNameList = emptyList()
        gymLevelList = emptyList()
        routeFilter.value = RouteFilter(1)
        _uiState.update {
            it.copy(
                curFloor = 1,
                firstFloorBtnState = FloorBtnState.FloorSelected,
                secondFloorBtnState = FloorBtnState.FloorUnSelected,
                sectorNameList = emptyList(),
                gymLevelList = emptyList(),
                routeList = emptyList(),
                selectedSector = SectorNameUiData {},
                selectedLevel = GymLevelUiData {},
                selectedRoute = RouteUiData {},
            )
        }
    }

    private fun renderCatalog(state: RouteCatalogState.Content) {
        val filter = state.filter
        sectorNameList = state.catalog.sectors.map { it.toSectorNameUiData(::selectSectorName) }
        gymLevelList = state.catalog.difficulties.map { it.toGymLevelUiData(::selectGymLevel) }
        val routes = state.catalog.routes.map { it.toRouteUiData(::selectRoute) }
        _uiState.update { uiState ->
            uiState.copy(
                isSingleFloor = state.catalog.maxFloor == 1,
                layoutImg = state.catalog.layoutImageUrl,
                curFloor = filter.floor,
                sectorNameList = sectorNameList.filter { it.floor == filter.floor }.map {
                    it.copy(isSelected = it.sectorId == filter.sectorId)
                },
                gymLevelList = gymLevelList.map { it.copy(isSelected = it.difficulty == filter.difficulty) },
                routeList = routes,
                selectedRoute = uiState.selectedRoute.keptIn(routes),
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

    fun selectRoute(item: RouteUiData) {
        _uiState.update { state ->
            state.copy(
                selectedRoute = item,
                clearBtnState = item.clearBtnState,
            )
        }
        challengeNumber.value = item.challengeNum

        addItem(item)
    }

    fun addChallengeNum() {
        challengeNumber.value += 1
        _items.value = _items.value.map {
            if (it.routeId ==
                uiState.value.selectedRoute.routeId
            ) {
                it.copy(challengeNum = it.challengeNum + 1)
            } else {
                it
            }
        }
    }

    fun subChallengeNum() {
        val currentValue = challengeNumber.value
        if (currentValue > 1) {
            challengeNumber.value = currentValue - 1
        }
        _items.value = _items.value.map {
            if (it.routeId == uiState.value.selectedRoute.routeId && it.challengeNum > 1) {
                it.copy(challengeNum = it.challengeNum - 1)
            } else {
                it
            }
        }
    }

    fun animateImage() {
        val alphaAnimator = ValueAnimator.ofFloat(1f, 0f)

        alphaAnimator.duration = 2000

        alphaAnimator.addUpdateListener { animation ->
            alpha.postValue(animation.animatedValue as Float)
        }

        val animatorSet = AnimatorSet()
        animatorSet.play(alphaAnimator)
        animatorSet.duration = 2000
        animatorSet.start()
    }

    fun setClear() {
        _uiState.update { state ->
            state.copy(clearBtnState = !state.clearBtnState)
        }
        _items.value = _items.value.map {
            if (it.routeId == uiState.value.selectedRoute.routeId) {
                it.copy(clearBtnState = !it.clearBtnState)
            } else {
                it
            }
        }

        if (uiState.value.clearBtnState) {
            animateImage()
        }
    }

    fun setToggle() {
        isToggleOn.value = !isToggleOn.value
    }

    private fun addItem(item: RouteUiData) {
        if (_items.value.none { it.routeId == item.routeId && it.sectorId == item.sectorId }) {
            val newItem = item.copy()
            _items.value += newItem
        }
    }

    fun showDeleteDialog(
        context: Context,
        id: Long,
    ) {
        val description = "기록이 삭제되면 복구할 수 없어요.\n정말 루트 기록을 삭제하시겠어요?"
        val rightText = "삭제"
        val leftText = "취소"
        val dialog = DeleteDialog(context, description, rightText, leftText) { isDelete ->
            if (isDelete) {
                removeItem(id)
            }
        }
        dialog.show()
    }

    fun removeItem(id: Long) {
        _items.value = _items.value.filter { it.routeId != id }
        _uiState.update { state ->
            state.copy(selectedRoute = RouteUiData {})
        }
    }

    private fun getTimeDiff(): LocalTime {
        val start = selectedStartTime.value
        val end = selectedEndTime.value
        if (start == end) return LocalTime.of(0, 0, 0)

        if (start.isBefore(end)) {
            val totalSeconds = ChronoUnit.SECONDS.between(start, end)

            return timeCalc(totalSeconds)
        } else {
            val totalSeconds = ChronoUnit.SECONDS.between(end, start)
            val diff = 86400 - totalSeconds

            return timeCalc(diff)
        }
    }

    private fun timeCalc(time: Long): LocalTime {
        val hours = time / 3600
        val minutes = (time % 3600) / 60
        val seconds = time % 60

        return LocalTime.of(hours.toInt(), minutes.toInt(), seconds.toInt())
    }

    fun climbingComplete() {
        val climbingRecords: List<ClimbingRecord> = _items.value.map { routeUiData ->
            ClimbingRecord(
                routeId = routeUiData.routeId,
                attemptCount = routeUiData.challengeNum,
                isCompleted = routeUiData.clearBtnState,
            )
        }

        val gymId = selectedCragEvent.value.first

        val requestBody = CreateClimbingRecordRequest(
            gymId = gymId,
            date = CreateRecordData.selectedDate.toString(),
            time = getTimeDiff().toString(),
            avgDifficulty = _items.value.map { it.difficulty }
                .average()
                .toInt(),
            routeRecordRequestDtoList = climbingRecords,
        )

        viewModelScope.launch {
            repository.createClimbingRecord(requestBody).let {
                when (it) {
                    is BaseState.Success -> {
                        _event.emit(CreateClimbingRecordEvent.ClimbingComplete)
                    }

                    is BaseState.Error -> {
                        _event.emit(CreateClimbingRecordEvent.ShowToastMessage(it.msg))
                    }
                }
            }
        }
    }

    fun navigateToSelectCrag() {
        resetCrag()
        viewModelScope.launch {
            _event.emit(CreateClimbingRecordEvent.NavigateToSelectCrag)
        }
    }

    fun navigateToBack() {
        viewModelScope.launch {
            _event.emit(CreateClimbingRecordEvent.NavigateToBack)
        }
    }

    fun resetState() {
        timePickText.value = defaultTimeText
        CreateRecordData.setSelectedTime(LocalTime.now(), defaultTime)
        resetCrag()
    }

    fun resetCrag() {
        _uiState.value = CreateClimbingRecordUiState()
        _items.value = emptyList()
        sectorNameList = emptyList()
        gymLevelList = emptyList()
        isSelectedCrag.value = false
        selectedCragEvent.value = defaultCrag
        challengeNumber.value = 0
        isToggleOn.value = true
    }
}
