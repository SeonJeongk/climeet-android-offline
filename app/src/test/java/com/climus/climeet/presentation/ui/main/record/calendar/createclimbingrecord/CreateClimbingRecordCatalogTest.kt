package com.climus.climeet.presentation.ui.main.record.calendar.createclimbingrecord

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.model.ErrorType
import com.climus.climeet.data.repository.MainRepository
import com.climus.climeet.data.repository.route.CachedRoute
import com.climus.climeet.presentation.ui.main.global.gymprofile.route.FakeRouteCatalogRepository
import com.climus.climeet.presentation.ui.main.global.gymprofile.route.routeCatalog
import com.climus.climeet.presentation.ui.main.global.selectsector.FloorBtnState
import com.climus.climeet.presentation.ui.main.global.selectsector.model.RouteUiData
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateClimbingRecordCatalogTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeRouteCatalogRepository()
    private lateinit var viewModel: CreateClimbingRecordViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        ArchTaskExecutor.getInstance().setDelegate(
            object : TaskExecutor() {
                override fun executeOnDiskIO(runnable: Runnable) = runnable.run()

                override fun postToMainThread(runnable: Runnable) = runnable.run()

                override fun isMainThread() = true
            },
        )
        viewModel = CreateClimbingRecordViewModel(mockk<MainRepository>(relaxed = true), repository)
    }

    @After
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
    }

    @Test
    fun otherGymWithoutCatalog_selectCrag_clearsPreviousRoutesAndSelection() =
        runTest(dispatcher) {
            viewModel.selectCrag(1L, "A")
            advanceUntilIdle()
            viewModel.uiState.value.routeList.first().let { it.onClickListener(it) }
            assertEquals(1L, viewModel.uiState.value.selectedRoute.routeId)

            repository.failure = BaseState.Error("offline", "OFFLINE", type = ErrorType.TRANSPORT)
            viewModel.selectCrag(2L, "B")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.routeList.isEmpty())
            assertEquals(RouteUiData {}.routeId, viewModel.uiState.value.selectedRoute.routeId)
        }

    @Test
    fun selectedRouteRemoved_catalogReplaced_clearsSelection() =
        runTest(dispatcher) {
            viewModel.selectCrag(1L, "A")
            advanceUntilIdle()
            viewModel.uiState.value.routeList.first().let { it.onClickListener(it) }

            val replacement = listOf(CachedRoute(9, 101, "A", "V3", 3, "V3", "#fff", "", "하양"))
            repository.replaceCatalog(routeCatalog(routes = replacement))
            advanceUntilIdle()

            assertEquals(RouteUiData {}.routeId, viewModel.uiState.value.selectedRoute.routeId)
        }

    @Test
    fun secondFloorSelected_otherDate_resetsFloorButtonsAndFilter() =
        runTest(dispatcher) {
            viewModel.selectCrag(1L, "A")
            advanceUntilIdle()
            viewModel.selectFloor(2)
            advanceUntilIdle()

            viewModel.setSelectedDate(viewModel.selectedDate.value.plusDays(1))
            advanceUntilIdle()

            assertEquals(FloorBtnState.FloorSelected, viewModel.uiState.value.firstFloorBtnState)
            assertEquals(FloorBtnState.FloorUnSelected, viewModel.uiState.value.secondFloorBtnState)
            assertEquals(listOf(1L), viewModel.uiState.value.routeList.map { it.routeId })
            assertEquals(1, viewModel.uiState.value.curFloor)
        }

    @Test
    fun sameDateAgain_setSelectedDate_keepsFloorAndFilter() =
        runTest(dispatcher) {
            viewModel.selectCrag(1L, "A")
            advanceUntilIdle()
            viewModel.selectFloor(2)
            advanceUntilIdle()
            viewModel.uiState.value.sectorNameList.single().let { it.onClickListener(it) }
            advanceUntilIdle()

            viewModel.setSelectedDate(viewModel.selectedDate.value)
            advanceUntilIdle()

            assertEquals(listOf(2L), viewModel.uiState.value.routeList.map { it.routeId })
            assertEquals(FloorBtnState.FloorSelected, viewModel.uiState.value.secondFloorBtnState)
            assertEquals(201L, viewModel.uiState.value.selectedSector.sectorId)
        }

    @Test
    fun selectedSector_selectFloor_doesNotCarryOldSectorIntoLevelFilter() =
        runTest(dispatcher) {
            viewModel.selectCrag(1L, "A")
            advanceUntilIdle()
            viewModel.uiState.value.sectorNameList.single().let { it.onClickListener(it) }
            advanceUntilIdle()

            viewModel.selectFloor(2)
            advanceUntilIdle()
            viewModel.uiState.value.gymLevelList.single().let { it.onClickListener(it) }
            advanceUntilIdle()

            assertEquals(listOf(2L), viewModel.uiState.value.routeList.map { it.routeId })
        }

    @Test
    fun filterChange_selectFloor_keepsOneObservation() =
        runTest(dispatcher) {
            viewModel.selectCrag(1L, "A")
            advanceUntilIdle()

            viewModel.selectFloor(2)
            advanceUntilIdle()

            assertEquals(1, repository.observations.size)
            assertEquals(listOf(2L), viewModel.uiState.value.routeList.map { it.routeId })
        }

    @Test
    fun missingCatalog_filterChange_doesNotRepeatToast() =
        runTest(dispatcher) {
            val toasts = mutableListOf<CreateClimbingRecordEvent>()
            val job = launch { viewModel.event.collect { toasts += it } }
            advanceUntilIdle()

            repository.failure = BaseState.Error("offline", "OFFLINE", type = ErrorType.TRANSPORT)
            viewModel.selectCrag(1L, "A")
            advanceUntilIdle()
            job.cancel()

            assertEquals(1, toasts.size)
            assertEquals(CreateClimbingRecordEvent.ShowToastMessage("offline"), toasts.single())
        }
}
