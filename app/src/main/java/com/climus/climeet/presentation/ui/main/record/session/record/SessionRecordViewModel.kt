package com.climus.climeet.presentation.ui.main.record.session.record

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
import com.climus.climeet.data.repository.MainRepository
import com.climus.climeet.data.repository.route.RouteCatalog
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
import com.climus.climeet.presentation.ui.main.record.session.record.draft.SessionDraftStore
import com.climus.climeet.presentation.ui.main.record.session.record.draft.recoverStopwatch
import com.climus.climeet.presentation.ui.main.record.session.record.draft.restoreRouteItems
import com.climus.climeet.presentation.ui.main.record.session.record.draft.summarizeSessionDraft
import com.climus.climeet.presentation.ui.main.record.session.record.policy.adjustedRecordCount
import com.climus.climeet.presentation.ui.main.record.session.record.policy.closestLevelName
import com.climus.climeet.presentation.ui.main.record.session.record.submission.SessionSubmissionController
import com.climus.climeet.presentation.ui.main.record.session.record.submission.SessionSubmissionState
import com.climus.climeet.presentation.util.formatStopwatchTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class SessionRecordUiState(
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

sealed class SessionRecordEvent {
    data class ShowToastMessage(
        val msg: String,
    ) : SessionRecordEvent()
}

@HiltViewModel
class SessionRecordViewModel @Inject constructor(
    private val repository: MainRepository,
    private val routeCatalogRepository: RouteCatalogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SessionRecordUiState())
    val uiState: StateFlow<SessionRecordUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<SessionRecordEvent>()
    val event: SharedFlow<SessionRecordEvent> = _event.asSharedFlow()

    private val submissionController = SessionSubmissionController(repository)
    private val draftStore = SessionDraftStore(repository)
    val submissionState = submissionController.state
    val submissionEffect = submissionController.effect
    val isCatalogRetryAvailable = MutableStateFlow(false)
    val restoredDuration = MutableStateFlow<String?>(null)
    val restoredElapsedTimeMillis = MutableStateFlow<Long?>(null)

    private val _items = MutableStateFlow<List<RouteUiData>>(emptyList())
    val items: StateFlow<List<RouteUiData>> = _items.asStateFlow()
    val itemsLiveData: LiveData<List<RouteUiData>> = _items.asLiveData()

    var gymId: Long = 0
    var gymName: String = ""
    private var isCatalogRequestStarted: Boolean = false
    private var catalogJob: Job? = null
    private var catalogKey: RouteCatalogKey? = null
    private var catalog: RouteCatalog? = null
    private var restoredDraftDate: LocalDate? = null

    private var sectorNameList = listOf<SectorNameUiData>()
    private var gymLevelList = listOf<GymLevelUiData>()

    val isGymSelected = MutableStateFlow(false)
    private val selectedGymData = MutableLiveData<Pair<Long, String>>()

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

    val routeRecordsChanged = MutableLiveData(false)
    val isRestoringDraft = MutableLiveData(false)
    val draftDetailsRestored = MutableLiveData(false)
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
            gymId = draft.gymId
            gymName = draft.gymName
            restoredDraftDate = runCatching { LocalDate.parse(draft.date) }.getOrNull()
            val recoveredStopwatch = recoverStopwatch(draft.time, sharedPreferences.getLong(PAUSE_TIME, 0L))
            restoredElapsedTimeMillis.value = recoveredStopwatch?.elapsedTimeMillis
            restoredDuration.value = recoveredStopwatch?.displayDuration
            restoreSelectedGym(draft.gymId, draft.gymName)
        }
    }

    /** 저장된 암장의 catalog를 준비한 뒤 Room의 route 기록을 화면 항목에 다시 연결합니다. */
    fun restoreSelectedGym(
        id: Long,
        name: String,
    ) {
        if (id <= 0L || name.isBlank()) return
        isRestoringDraft.value = true
        if (isCatalogRequestStarted && gymId == id) {
            restoreRouteRecords()
        } else {
            selectGym(id, name)
        }
    }

    fun submitRecord(elapsedTimeMillis: Long) {
        if (submissionState.value != SessionSubmissionState.Draft) return
        restoredElapsedTimeMillis.value = elapsedTimeMillis
        restoredDuration.value = formatStopwatchTime(elapsedTimeMillis)
        viewModelScope.launch {
            submissionController.submit(elapsedTimeMillis)
        }
    }

    fun discardPreservedDraft() {
        if (submissionState.value !is SessionSubmissionState.Preserved) return
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
        if (!isCatalogRetryAvailable.value || gymId <= 0L) return
        isCatalogRequestStarted = false
        selectGym(gymId, gymName)
    }

    fun prepareGymChange() {
        isCatalogRequestStarted = false
    }

    private fun mutateDraft(block: suspend () -> Unit) {
        if (submissionState.value != SessionSubmissionState.Draft) return
        viewModelScope.launch {
            submissionController.mutateDraft(block)
        }
    }

    private fun setTotalValue() {
        totalRoute.value = sharedPreferences.getString(PREF_TOTAL_ATTEMPTS, "--")
        totalComplete.value = sharedPreferences.getString(PREF_TOTAL_COMPLETIONS, "--")
        avgLevel.value = sharedPreferences.getString(PREF_AVERAGE_LEVEL, "--")
    }

    private fun restoreRouteRecords() {
        viewModelScope.launch(Dispatchers.IO) {
            val routeDataList = repository.getAllRoute()

            if (_items.value.isEmpty()) {
                val restoredItems = restoreRouteItems(routeDataList, firstFloorRouteList + secondFloorRouteList)
                withContext(Dispatchers.Main) {
                    isGymSelected.value = true
                    val summary = summarizeSessionDraft(routeDataList)
                    totalRoute.value = summary.routeCount
                    totalComplete.value = summary.completedCount
                    _items.value = restoredItems
                }
                sharedPreferences.edit {
                    putString(PREF_TOTAL_ATTEMPTS, totalRoute.value)
                    putString(PREF_TOTAL_COMPLETIONS, totalComplete.value)
                }
                refreshAverageLevel()
                draftDetailsRestored.postValue(true)
            }
        }
    }

    fun selectGym(
        id: Long,
        name: String,
    ) {
        if (!isCatalogRequestStarted) {
            selectedGymData.value = Pair(id, name)
            gymId = id
            gymName = name
            loadGymCatalog(gymId)
            isCatalogRequestStarted = true
        }
    }

    private fun loadGymCatalog(id: Long) {
        if (id <= 0L) return
        // 화면이 자정을 넘겨 살아 있어도 새 기록은 오늘 카탈로그를 읽습니다.
        val key = RouteCatalogKey(id, restoredDraftDate ?: LocalDate.now())
        if (key != catalogKey) clearCatalog()
        catalogKey = key
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            routeCatalogRepository.observe(key, flowOf(RouteFilter(0))).collect { state ->
                when (state) {
                    RouteCatalogState.Loading -> Unit
                    is RouteCatalogState.Failed -> {
                        isCatalogRequestStarted = false
                        isCatalogRetryAvailable.value = true
                        _event.emit(SessionRecordEvent.ShowToastMessage(CATALOG_RETRY_MESSAGE))
                    }

                    is RouteCatalogState.Content -> applyCatalog(state.catalog)
                }
            }
        }
    }

    private fun applyCatalog(loaded: RouteCatalog) {
        isCatalogRetryAvailable.value = false
        // 갱신 상태만 바뀐 경우에는 사용자가 고른 층과 필터를 유지합니다.
        if (loaded != catalog) {
            catalog = loaded
            sectorNameList = loaded.sectors.map { it.toSectorNameUiData(::selectSectorName) }
            gymLevelList = loaded.difficulties.map { it.toGymLevelUiData(::selectGymLevel) }
            maxFloor = loaded.maxFloor
            isSecondFloorExist = loaded.maxFloor > 1
            val allRoutes = loaded.routes.map { it.toRouteUiData(::selectRoute) }
            firstFloorRouteList = allRoutes.filter { route ->
                loaded.sectors.any { it.floor == 1 && it.sectorId == route.sectorId }
            }
            secondFloorRouteList = allRoutes.filter { route ->
                loaded.sectors.any { it.floor == 2 && it.sectorId == route.sectorId }
            }
            renderCatalog(1)
            _uiState.update { it.copy(selectedRoute = it.selectedRoute.keptIn(allRoutes)) }
        }
        if (isRestoringDraft.value == true) {
            isRestoringDraft.value = false
            restoreRouteRecords()
        }
    }

    /** 다른 암장의 루트로 기록하지 않도록 이전 목록과 선택을 지웁니다. */
    private fun clearCatalog() {
        catalog = null
        sectorNameList = emptyList()
        gymLevelList = emptyList()
        firstFloorRouteList = emptyList()
        secondFloorRouteList = emptyList()
        _uiState.update {
            it.copy(
                sectorNameList = emptyList(),
                gymLevelList = emptyList(),
                routeList = emptyList(),
                selectedSector = SectorNameUiData {},
                selectedLevel = GymLevelUiData {},
                selectedRoute = RouteUiData {},
            )
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
        loadFloorRoutes(floor)
    }

    private fun loadFloorRoutes(floor: Int) {
        renderCatalog(floor)
    }

    private fun loadFilteredRoutes(
        floor: Int,
        sectorId: Long? = null,
        difficulty: Int? = null,
    ) {
        gymId = selectedGymData.value?.first ?: run { 0 }
        renderCatalog(floor, sectorId, difficulty)
    }

    private fun renderCatalog(
        floor: Int,
        sectorId: Long? = null,
        difficulty: Int? = null,
    ) {
        val loaded = catalog ?: return
        val routes = loaded.routes
            .filter { route ->
                loaded.sectors.any { it.floor == floor && it.sectorId == route.sectorId }
            }
            .filter { sectorId == null || it.sectorId == sectorId }
            .filter { difficulty == null || it.difficulty == difficulty }
            .map { it.toRouteUiData(::selectRoute) }
        _uiState.update { state ->
            state.copy(
                isSingleFloor = loaded.maxFloor == 1,
                layoutImg = loaded.layoutImageUrl,
                sectorNameList = sectorNameList.filter { it.floor == floor }.map {
                    it.copy(isSelected = it.sectorId == sectorId)
                },
                gymLevelList = gymLevelList.map { it.copy(isSelected = it.difficulty == difficulty) },
                routeList = routes,
                curFloor = floor,
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

        loadFilteredRoutes(
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

        loadFilteredRoutes(
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
        if (submissionState.value != SessionSubmissionState.Draft) return
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
        if (submissionState.value != SessionSubmissionState.Draft) return
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
        if (submissionState.value != SessionSubmissionState.Draft) return
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
        if (submissionState.value != SessionSubmissionState.Draft) return
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
        if (submissionState.value != SessionSubmissionState.Draft) return
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
        if (submissionState.value != SessionSubmissionState.Draft) return
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
        if (submissionState.value != SessionSubmissionState.Draft) return
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
        _uiState.value = SessionRecordUiState()
        _items.value = emptyList()
        gymId = 0
        gymName = ""
        restoredDuration.value = null
        restoredElapsedTimeMillis.value = null
        totalRoute.value = "--"
        totalComplete.value = "--"
        avgLevel.value = "--"
        sharedPreferences.edit {
            putString(PREF_TOTAL_ATTEMPTS, "--")
            putString(PREF_TOTAL_COMPLETIONS, "--")
            putString(PREF_AVERAGE_LEVEL, "--")
            remove("gymId")
            remove("gymName")
        }
        isCatalogRequestStarted = false
        isCatalogRetryAvailable.value = false
        catalogKey = null
        restoredDraftDate = null
        isSecondFloorExist = false
        firstFloorRouteList = emptyList()
        secondFloorRouteList = emptyList()
    }

    private fun saveRouteRecord(item: RouteUiData) {
        mutateDraft {
            if (draftStore.saveRoute(item)) {
                routeRecordsChanged.postValue(!(routeRecordsChanged.value ?: false))
                refreshAverageLevel()
            }
        }

        updateTotalCount(totalRoute, PREF_TOTAL_ATTEMPTS, true)
    }

    private fun setRoomChallengeNum(
        item: RouteUiData,
        plus: Boolean,
    ) {
        mutateDraft {
            if (draftStore.changeAttempt(item, if (plus) 1 else -1)) {
                routeRecordsChanged.postValue(!(routeRecordsChanged.value ?: false))
            }
        }
    }

    private fun setComplete(
        item: RouteUiData,
        complete: Boolean,
    ) {
        mutateDraft {
            if (draftStore.setCompleted(item, complete)) {
                routeRecordsChanged.postValue(!(routeRecordsChanged.value ?: false))
                refreshAverageLevel()
            }
        }

        updateTotalCount(totalComplete, PREF_TOTAL_COMPLETIONS, complete)
    }

    private fun deleteRouteRecord(
        item: RouteUiData,
        isCompleted: Boolean,
    ) {
        mutateDraft {
            if (draftStore.delete(item)) {
                routeRecordsChanged.postValue(!(routeRecordsChanged.value ?: false))
                refreshAverageLevel()
            }
        }

        if (updateTotalCount(totalRoute, PREF_TOTAL_ATTEMPTS, false) && isCompleted) {
            updateTotalCount(totalComplete, PREF_TOTAL_COMPLETIONS, false)
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
        sharedPreferences.edit { putString(PREF_AVERAGE_LEVEL, name) }
    }

    companion object {
        const val PREF_TOTAL_ATTEMPTS = "timerChallenge"
        const val PREF_TOTAL_COMPLETIONS = "timerComplete"
        const val PREF_AVERAGE_LEVEL = "timerLevel"
        private const val PAUSE_TIME = "pauseTime"
        private const val CATALOG_RETRY_MESSAGE = "암장 정보를 불러오지 못했습니다. 암장 이름을 눌러 다시 시도해주세요."
    }
}
