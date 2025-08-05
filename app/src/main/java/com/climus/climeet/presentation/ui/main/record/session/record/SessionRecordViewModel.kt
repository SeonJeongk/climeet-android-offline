package com.climus.climeet.presentation.ui.main.record.timer.setrecord

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.climus.climeet.app.App.Companion.sharedPreferences
import com.climus.climeet.data.local.ClimbingRecordData
import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.repository.MainRepository
import com.climus.climeet.presentation.customview.DeleteDialog
import com.climus.climeet.presentation.ui.main.global.selectsector.FloorBtnState
import com.climus.climeet.presentation.ui.main.global.selectsector.model.GymLevelUiData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.RouteUiData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.SectorNameUiData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.SelectedFilter
import com.climus.climeet.presentation.ui.main.global.toGymLevelUiData
import com.climus.climeet.presentation.ui.main.global.toRouteUiData
import com.climus.climeet.presentation.ui.main.global.toSectorNameUiData
import com.climus.climeet.presentation.util.formatTimerTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CreateRecordUiState(
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

sealed class CreateRecordEvent {
    data class ShowToastMessage(
        val msg: String,
    ) : CreateRecordEvent()
}

@HiltViewModel
class SetTimerClimbingRecordViewModel @Inject constructor(
    private val repository: MainRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateRecordUiState())
    val uiState: StateFlow<CreateRecordUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<CreateRecordEvent>()
    val event: SharedFlow<CreateRecordEvent> = _event.asSharedFlow()

    private val submissionController = TimerRecordSubmissionController(repository)
    private val draftStore = TimerRecordDraftStore(repository)
    private val catalogLoader = TimerRouteCatalogLoader(repository)
    val submissionState = submissionController.state
    val submissionEffect = submissionController.effect
    val isCatalogRetryAvailable = MutableStateFlow(false)
    val restoredDuration = MutableStateFlow<String?>(null)
    val restoredElapsedTimeMillis = MutableStateFlow<Long?>(null)

    private val _items = MutableStateFlow<List<RouteUiData>>(emptyList())
    val items: StateFlow<List<RouteUiData>> = _items.asStateFlow()
    val itemsLiveData: LiveData<List<RouteUiData>> = _items.asLiveData()

    var cragId: Long = 0
    var cragName: String = ""
    var apiCheck: Boolean = false

    private var sectorNameList = listOf<SectorNameUiData>()
    private var gymLevelList = listOf<GymLevelUiData>()

    val isSelectedCrag = MutableStateFlow(false)
    private val selectedCragEvent = MutableLiveData<Pair<Long, String>>()

    private val _challengeNumber = MutableLiveData(0)
    val challengeNumber: LiveData<Int> = _challengeNumber

    val isAvgToggleOn = MutableLiveData(true)
    val isRouteToggleOn = MutableLiveData(true)

    val alpha = MutableLiveData(1f)

    val totalRoute = MutableLiveData("--")
    val totalComplete = MutableLiveData("--")
    val avgLevel = MutableLiveData("--")
    val avgCompleteRate: MediatorLiveData<Double> = MediatorLiveData<Double>().apply {
        addSource(totalRoute) { recalculateAvgRate() }
        addSource(totalComplete) { recalculateAvgRate() }
    }

    val updateRecord = MutableLiveData(false)
    val isRestart = MutableLiveData(false)
    val resetView = MutableLiveData(false)
    private var isSecondFloorExist: Boolean = false

    private var maxFloor = 0
    private var firstFloorRouteList: List<RouteUiData> = emptyList()
    private var secondFloorRouteList: List<RouteUiData> = emptyList()

    fun clearItems() {
        _items.value = emptyList()
    }

    private fun recalculateAvgRate() {
        if (totalRoute.value == "--" || totalComplete.value == "--") {
            avgCompleteRate.value = 0.0
        } else {
            val total = totalRoute.value?.toDoubleOrNull()
            val complete = totalComplete.value?.toDoubleOrNull()

            if (total != null && complete != null && total != 0.0) {
                avgCompleteRate.value = complete / total
            } else {
                avgCompleteRate.value = 0.0
            }
        }
    }

    init {
        setTotalValue()
        restoreDraft()
    }

    fun saveDraft(record: ClimbingRecordData) {
        mutateDraft {
            draftStore.saveClimbingDraft(record)
        }
    }

    fun restoreDraft() {
        viewModelScope.launch {
            val draft = submissionController.restoreDraft() ?: return@launch
            cragId = draft.gymId
            cragName = draft.gymName
            val recoveredTimer = recoverTimer(draft.time, sharedPreferences.getLong(PAUSE_TIME, 0L))
            restoredElapsedTimeMillis.value = recoveredTimer?.elapsedTimeMillis
            restoredDuration.value = recoveredTimer?.displayDuration
            restoreSelectedCrag(draft.gymId, draft.gymName)
        }
    }

    fun restoreSelectedCrag(
        id: Long,
        name: String,
    ) {
        if (id <= 0L || name.isBlank()) return
        isRestart.value = true
        if (apiCheck && cragId == id) {
            getClimbingData()
        } else {
            selectedCrag(id, name)
        }
    }

    fun submitRecord(elapsedTimeMillis: Long) {
        if (submissionState.value != TimerSubmissionState.Draft) return
        restoredElapsedTimeMillis.value = elapsedTimeMillis
        restoredDuration.value = formatTimerTime(elapsedTimeMillis)
        viewModelScope.launch {
            submissionController.submit(elapsedTimeMillis)
        }
    }

    fun discardPreservedDraft() {
        if (submissionState.value !is TimerSubmissionState.Preserved) return
        viewModelScope.launch {
            if (submissionController.discardPreservedDraft()) {
                resetAtStop()
            }
        }
    }

    fun acknowledgeSubmissionSuccess() {
        viewModelScope.launch {
            submissionController.acknowledgeSuccess()
        }
    }

    fun retryCatalog() {
        if (!isCatalogRetryAvailable.value || cragId <= 0L) return
        apiCheck = false
        selectedCrag(cragId, cragName)
    }

    private fun mutateDraft(block: suspend () -> Unit) {
        if (submissionState.value != TimerSubmissionState.Draft) return
        viewModelScope.launch {
            submissionController.mutateDraft(block)
        }
    }

    private fun setTotalValue() {
        totalRoute.value = sharedPreferences.getString(TOP_CHALLENGE, "--")
        totalComplete.value = sharedPreferences.getString(TOP_COMPLETE, "--")
        avgLevel.value = sharedPreferences.getString(TOP_LEVEL, "--")
    }

    private fun getClimbingData() {
        viewModelScope.launch(Dispatchers.IO) {
            val routeDataList = repository.getAllRoute()

            if (_items.value.isEmpty()) {
                val restoredItems = restoreRouteItems(routeDataList, firstFloorRouteList + secondFloorRouteList)
                withContext(Dispatchers.Main) {
                    isSelectedCrag.value = true
                    val summary = summarizeTimerDraft(routeDataList)
                    totalRoute.value = summary.routeCount
                    totalComplete.value = summary.completedCount
                    _items.value = restoredItems
                }
                sharedPreferences.edit {
                    putString(TOP_CHALLENGE, totalRoute.value)
                    putString(TOP_COMPLETE, totalComplete.value)
                }
                refreshAverageLevel()
                resetView.postValue(true)
            }
        }
    }

    fun selectedCrag(
        id: Long,
        name: String,
    ) {
        if (!apiCheck) {
            selectedCragEvent.value = Pair(id, name)
            cragId = id
            cragName = name
            getCragInfo(cragId)
            apiCheck = true
        }
    }

    private fun getCragInfo(id: Long) {
        viewModelScope.launch {
            repository.getGymFilteringKey(id).let {
                when (it) {
                    is BaseState.Success -> {
                        isCatalogRetryAvailable.value = false
                        sectorNameList = it.body.sectorList.map { data ->
                            data.toSectorNameUiData(::selectSectorName)
                        }

                        gymLevelList = it.body.difficultyList.map { data ->
                            data.toGymLevelUiData(::selectGymLevel)
                        }

                        if (it.body.maxFloor == 1) {
                            _uiState.update { state ->
                                state.copy(
                                    isSingleFloor = true,
                                    layoutImg = it.body.layoutImageUrl,
                                )
                            }
                            maxFloor = 1
                            isSecondFloorExist = false
                        } else {
                            _uiState.update { state ->
                                state.copy(
                                    isSingleFloor = false,
                                    layoutImg = it.body.layoutImageUrl,
                                )
                            }
                            maxFloor = 2
                            isSecondFloorExist = true
                            if (!loadFloorInfo(2, publish = false)) return@let
                        }
                        if (loadFloorInfo(1, publish = true) && isRestart.value == true) {
                            isRestart.value = false
                            getClimbingData()
                        }
                    }

                    is BaseState.Error -> {
                        apiCheck = false
                        isCatalogRetryAvailable.value = true
                        _event.emit(CreateRecordEvent.ShowToastMessage(CATALOG_RETRY_MESSAGE))
                    }
                }
            }
        }
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
        viewModelScope.launch {
            loadFloorInfo(floor, publish = true)
        }
    }

    private suspend fun loadFloorInfo(
        floor: Int,
        publish: Boolean,
    ): Boolean =
        when (val result = catalogLoader.load(cragId, floor)) {
            is BaseState.Success -> {
                val routes = result.body.map { data -> data.toRouteUiData(::selectRoute) }
                isCatalogRetryAvailable.value = false
                if (floor == 1) firstFloorRouteList = routes else secondFloorRouteList = routes
                if (publish) {
                    _uiState.update { state ->
                        state.copy(
                            sectorNameList = sectorNameList.filter { data -> data.floor == floor },
                            gymLevelList = gymLevelList,
                            routeList = routes,
                            curFloor = floor,
                        )
                    }
                }
                true
            }

            is BaseState.Error -> {
                apiCheck = false
                isCatalogRetryAvailable.value = true
                _event.emit(CreateRecordEvent.ShowToastMessage(CATALOG_RETRY_MESSAGE))
                false
            }
        }

    private fun getRouteList(
        floor: Int,
        sectorId: Long? = null,
        difficulty: Int? = null,
    ) {
        cragId = selectedCragEvent.value?.first ?: run { 0 }
        viewModelScope.launch {
            catalogLoader.load(cragId, floor, sectorId, difficulty).let {
                when (it) {
                    is BaseState.Success -> {
                        isCatalogRetryAvailable.value = false
                        _uiState.update { state ->
                            state.copy(
                                routeList = it.body.map { data ->
                                    data.toRouteUiData(::selectRoute)
                                },
                            )
                        }
                    }

                    is BaseState.Error -> {
                        isCatalogRetryAvailable.value = true
                        _event.emit(CreateRecordEvent.ShowToastMessage(CATALOG_RETRY_MESSAGE))
                    }
                }
            }
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
        _challengeNumber.value = item.challengeNum

        addItem(item)
    }

    fun setChallengeNum(count: Int) {
        _challengeNumber.value = count
    }

    fun addChallengeNum() {
        if (submissionState.value != TimerSubmissionState.Draft) return
        _challengeNumber.value = (_challengeNumber.value ?: 0) + 1
        _items.value = _items.value.map {
            if (it.routeId ==
                uiState.value.selectedRoute.routeId
            ) {
                it.copy(challengeNum = it.challengeNum + 1)
            } else {
                it
            }
        }
        _items.value.filter { it.routeId == uiState.value.selectedRoute.routeId }.forEach {
            setRoomChallengeNum(it, true)
        }
    }

    fun subChallengeNum() {
        if (submissionState.value != TimerSubmissionState.Draft) return
        val currentValue = _challengeNumber.value ?: 0
        var check = false
        if (currentValue > 0) {
            _challengeNumber.value = currentValue - 1
            check = true
        }
        _items.value = _items.value.map {
            if (it.routeId == uiState.value.selectedRoute.routeId && it.challengeNum > 0) {
                it.copy(challengeNum = it.challengeNum - 1)
            } else {
                it
            }
        }
        _items.value.filter { it.routeId == uiState.value.selectedRoute.routeId && check }.forEach {
            setRoomChallengeNum(it, false)
        }
    }

    private fun animateImage() {
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
        if (submissionState.value != TimerSubmissionState.Draft) return
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

        _items.value.filter { it.routeId == uiState.value.selectedRoute.routeId }.forEach {
            setComplete(it, uiState.value.clearBtnState)
            setAvgLevel()
        }
    }

    private fun addItem(item: RouteUiData) {
        if (_items.value.none { it.routeId == item.routeId && it.sectorId == item.sectorId }) {
            val newItem = item.copy(isSelected = true)
            _items.value += newItem

            saveRouteRecord(newItem)
        }
    }

    fun itemIncrease(
        id: Long,
        num: Int,
    ) {
        if (submissionState.value != TimerSubmissionState.Draft) return
        _items.value = _items.value.map {
            if (it.routeId == id) {
                if (uiState.value.selectedRoute.routeId == it.routeId) {
                    _challengeNumber.value = (_challengeNumber.value ?: 0) + num
                }
                it.copy(challengeNum = it.challengeNum + num)
            } else {
                it
            }
        }
        _items.value.filter { it.routeId == id }.forEach {
            setRoomChallengeNum(it, true)
        }
    }

    fun itemDecrease(id: Long) {
        if (submissionState.value != TimerSubmissionState.Draft) return
        var check = false
        _items.value = _items.value.map {
            if (it.routeId == id && it.challengeNum > 0) {
                if (uiState.value.selectedRoute.routeId == it.routeId) {
                    _challengeNumber.value = (_challengeNumber.value ?: 0) - 1
                    check = true
                }
                it.copy(challengeNum = it.challengeNum - 1)
            } else {
                it
            }
        }
        _items.value.filter { it.routeId == id && check }.forEach {
            setRoomChallengeNum(it, false)
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

    private fun removeItem(id: Long) {
        if (submissionState.value != TimerSubmissionState.Draft) return
        val itemToRemove = _items.value.find { it.routeId == id }
        if (itemToRemove != null) {
            deleteRouteRecord(itemToRemove, itemToRemove.clearBtnState)
            setAvgLevel()
        }
        _items.value = _items.value.filter { it.routeId != id }
        _uiState.update { state ->
            state.copy(selectedRoute = RouteUiData {})
        }
    }

    fun setBtnState(id: Long) {
        if (submissionState.value != TimerSubmissionState.Draft) return
        _items.value = _items.value.map {
            if (it.routeId == id) {
                if (uiState.value.selectedRoute.routeId == it.routeId) {
                    _uiState.update { state ->
                        state.copy(clearBtnState = !state.clearBtnState)
                    }
                }
                it.copy(clearBtnState = !it.clearBtnState)
            } else {
                it
            }
        }

        if (uiState.value.clearBtnState) {
            animateImage()
        }

        _items.value.filter { it.routeId == uiState.value.selectedRoute.routeId }.forEach {
            setComplete(it, uiState.value.clearBtnState)
            setAvgLevel()
        }
    }

    fun setAvgToggle() {
        isAvgToggleOn.value = !(isAvgToggleOn.value ?: false)
    }

    fun setRouteToggle() {
        isRouteToggleOn.value = !(isRouteToggleOn.value ?: false)
    }

    fun resetAtStop() {
        _uiState.value = CreateRecordUiState()
        _items.value = emptyList()
        cragId = 0
        cragName = ""
        restoredDuration.value = null
        restoredElapsedTimeMillis.value = null
        totalRoute.value = "--"
        totalComplete.value = "--"
        avgLevel.value = "--"
        sharedPreferences.edit {
            putString(TOP_CHALLENGE, "--")
            putString(TOP_COMPLETE, "--")
            putString(TOP_LEVEL, "--")
            remove("cragId")
            remove("cragName")
        }
        apiCheck = false
        isCatalogRetryAvailable.value = false
        isSecondFloorExist = false
        firstFloorRouteList = emptyList()
        secondFloorRouteList = emptyList()
    }

    private fun saveRouteRecord(item: RouteUiData) {
        mutateDraft {
            if (draftStore.saveRoute(item)) {
                updateRecord.postValue(!(updateRecord.value ?: false))
                refreshAverageLevel()
            }
        }

        updateTotalCount(totalRoute, TOP_CHALLENGE, true)
    }

    private fun setRoomChallengeNum(
        item: RouteUiData,
        plus: Boolean,
    ) {
        mutateDraft {
            if (draftStore.changeAttempt(item, if (plus) 1 else -1)) {
                updateRecord.postValue(!(updateRecord.value ?: false))
            }
        }
    }

    private fun setComplete(
        item: RouteUiData,
        complete: Boolean,
    ) {
        mutateDraft {
            if (draftStore.setCompleted(item, complete)) {
                updateRecord.postValue(!(updateRecord.value ?: false))
                refreshAverageLevel()
            }
        }

        updateTotalCount(totalComplete, TOP_COMPLETE, complete)
    }

    private fun deleteRouteRecord(
        item: RouteUiData,
        isCompleted: Boolean,
    ) {
        mutateDraft {
            if (draftStore.delete(item)) {
                updateRecord.postValue(!(updateRecord.value ?: false))
                refreshAverageLevel()
            }
        }

        if (updateTotalCount(totalRoute, TOP_CHALLENGE, false) && isCompleted) {
            updateTotalCount(totalComplete, TOP_COMPLETE, false)
        }
    }

    private fun updateTotalCount(
        total: MutableLiveData<String>,
        preferenceKey: String,
        increase: Boolean,
    ): Boolean {
        val current = total.value ?: return false
        val updated = adjustedRecordCount(current, increase)
        total.value = updated
        sharedPreferences.edit { putString(preferenceKey, updated) }
        return true
    }

    private fun setAvgLevel() {
        viewModelScope.launch {
            submissionController.mutateDraft { refreshAverageLevel() }
        }
    }

    private suspend fun refreshAverageLevel() {
        val averageDifficulty = draftStore.averageDifficulty()
        val name = closestLevelName(
            averageDifficulty,
            gymLevelList.map { it.difficulty to it.levelName },
        ) ?: "--"
        avgLevel.postValue(name)
        sharedPreferences.edit { putString(TOP_LEVEL, name) }
    }

    companion object {
        const val TOP_CHALLENGE = "timerChallenge"
        const val TOP_COMPLETE = "timerComplete"
        const val TOP_LEVEL = "timerLevel"
        private const val PAUSE_TIME = "pauseTime"
        private const val CATALOG_RETRY_MESSAGE = "암장 정보를 불러오지 못했습니다. 암장 이름을 눌러 다시 시도해주세요."
    }
}

internal fun adjustedRecordCount(
    current: String,
    increase: Boolean,
): String =
    if (increase) {
        if (current == "--") "1" else (current.toInt() + 1).toString()
    } else {
        if (current == "1" || current == "0") "--" else (current.toInt() - 1).toString()
    }
