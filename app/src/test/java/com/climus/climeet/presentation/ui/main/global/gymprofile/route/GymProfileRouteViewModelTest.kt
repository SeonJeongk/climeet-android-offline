package com.climus.climeet.presentation.ui.main.global.gymprofile.route

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.model.ErrorType
import com.climus.climeet.data.model.request.GetGymRouteInfoRequest
import com.climus.climeet.data.model.response.DifficultyItem
import com.climus.climeet.data.model.response.GetGymFilteringKeyResponse
import com.climus.climeet.data.model.response.GetGymRouteInfoResponse
import com.climus.climeet.data.model.response.RouteItem
import com.climus.climeet.data.model.response.SectorItem
import com.climus.climeet.data.model.runRemote
import com.climus.climeet.data.repository.MainRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class GymProfileRouteViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val fake = RouteRepositoryFake()
    private lateinit var viewModel: GymProfileRouteViewModel

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
        viewModel = GymProfileRouteViewModel(fake.repository)
    }

    @After
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun invalidGymId_dateObserverStarts_skipsRepositoryCalls() =
        runTest(dispatcher) {
            viewModel.setDate()
            viewModel.setCragInfo(0, "")
            advanceUntilIdle()

            assertTrue(fake.keyRequests.isEmpty())
            assertTrue(fake.routeRequests.isEmpty())
        }

    @Test
    fun historicalDate_changeFloorAndFilters_keepsRequestSnapshot() =
        runTest(dispatcher) {
            viewModel.setSelectedDate(LocalDate.of(2026, 8, 12))
            viewModel.setDate()
            viewModel.setCragInfo(1, "Synthetic gym")
            advanceUntilIdle()
            viewModel.selectFloor(2)
            advanceUntilIdle()
            val sector = viewModel.uiState.value.sectorNameList.first()
            sector.onClickListener(sector)
            advanceUntilIdle()
            val level = viewModel.uiState.value.gymLevelList.first()
            level.onClickListener(level)
            advanceUntilIdle()

            assertTrue(fake.routeRequests.isNotEmpty())
            assertTrue(fake.routeRequests.all { it.second.timePoint == "2026-08-12" })
            assertEquals(GetGymRouteInfoRequest(0, 10, 2, 201, 3, "2026-08-12"), fake.routeRequests.last().second)
        }

    @Test
    fun secondFloor_responseCompletes_keepsSecondFloorSelected() =
        runTest(dispatcher) {
            viewModel.setCragInfo(1, "Synthetic gym")
            advanceUntilIdle()
            viewModel.selectFloor(2)
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.curFloor)
            assertEquals(2, fake.routeRequests.last().second.floor)
        }

    @Test
    fun staleSuccess_afterLatestSuccess_keepsLatestRoutes() =
        runTest(dispatcher) {
            viewModel.setCragInfo(1, "Synthetic gym")
            advanceUntilIdle()
            fake.routes = { request ->
                if (request.floor == 2) {
                    withContext(NonCancellable) { delay(800) }
                }
                BaseState.Success(GetGymRouteInfoResponse(0, false, listOf(route(request.floor!!.toLong()))))
            }
            viewModel.selectFloor(2)
            runCurrent()
            viewModel.selectFloor(1)
            advanceUntilIdle()

            assertEquals(listOf(1L), viewModel.uiState.value.routeList.map { it.routeId })
        }

    @Test
    fun staleFailure_afterLatestSuccess_doesNotEmitError() =
        runTest(dispatcher) {
            val events = mutableListOf<GymProfileRouteEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.event.collect(events::add) }
            viewModel.setCragInfo(1, "Synthetic gym")
            advanceUntilIdle()
            fake.routes = { request ->
                if (request.floor == 2) {
                    withContext(NonCancellable) { delay(800) }
                    BaseState.Error("old failure", "500")
                } else {
                    BaseState.Success(GetGymRouteInfoResponse(0, false, listOf(route(1))))
                }
            }
            viewModel.selectFloor(2)
            runCurrent()
            viewModel.selectFloor(1)
            advanceUntilIdle()

            assertTrue(events.none { it is GymProfileRouteEvent.ShowToastMessage })
            assertEquals(listOf(1L), viewModel.uiState.value.routeList.map { it.routeId })
        }

    @Test
    fun twentyFiveRoutes_loadsPages_publishesAllInOrder() =
        runTest(dispatcher) {
            viewModel.setSelectedDate(LocalDate.of(2026, 8, 12))
            viewModel.setCragInfo(1, "Synthetic gym")
            advanceUntilIdle()
            viewModel.selectFloor(2)
            advanceUntilIdle()
            val sector = viewModel.uiState.value.sectorNameList.single()
            sector.onClickListener(sector)
            advanceUntilIdle()
            fake.routeRequests.clear()
            fake.routes = { request ->
                val items = (1L..25L).map(::route).drop(request.page * request.size).take(request.size)
                BaseState.Success(GetGymRouteInfoResponse(request.page, request.page < 2, items))
            }
            val level = viewModel.uiState.value.gymLevelList.single()
            level.onClickListener(level)
            advanceUntilIdle()

            assertEquals((1L..25L).toList(), viewModel.uiState.value.routeList.map { it.routeId })
            assertEquals(
                (0..2).map { 1L to GetGymRouteInfoRequest(it, 10, 2, 201, 3, "2026-08-12") },
                fake.routeRequests,
            )
        }

    @Test
    fun sameGymAndDate_repeatedObservers_doNotReload() =
        runTest(dispatcher) {
            viewModel.setCragInfo(1, "Synthetic gym")
            viewModel.setDate()
            advanceUntilIdle()
            viewModel.selectFloor(2)
            advanceUntilIdle()
            val sector = viewModel.uiState.value.sectorNameList.single()
            sector.onClickListener(sector)
            advanceUntilIdle()
            val level = viewModel.uiState.value.gymLevelList.single()
            level.onClickListener(level)
            advanceUntilIdle()
            val route = viewModel.uiState.value.routeList.single()
            route.onClickListener(route)
            val selectedState = viewModel.uiState.value
            val previousRequests = fake.routeRequests.toList()
            viewModel.setCragInfo(1, "Synthetic gym")
            viewModel.setDate()
            advanceUntilIdle()

            assertEquals(1, fake.keyRequests.size)
            assertEquals(previousRequests, fake.routeRequests)
            assertEquals(selectedState, viewModel.uiState.value)
        }

    @Test
    fun floorChanges_preservesDifficultyAndClearsIncompatibleSelection() =
        runTest(dispatcher) {
            viewModel.setCragInfo(1, "Synthetic gym")
            advanceUntilIdle()
            val level = viewModel.uiState.value.gymLevelList.first()
            level.onClickListener(level)
            advanceUntilIdle()
            val sector = viewModel.uiState.value.sectorNameList.first()
            sector.onClickListener(sector)
            advanceUntilIdle()
            val route = viewModel.uiState.value.routeList.first()
            route.onClickListener(route)
            viewModel.selectFloor(2)

            assertTrue(viewModel.uiState.value.routeList.isEmpty())
            assertEquals(-1L, viewModel.uiState.value.selectedRoute.routeId)
            assertEquals(-1L, viewModel.uiState.value.selectedSector.sectorId)
            assertEquals(3, viewModel.uiState.value.selectedLevel.difficulty)
            assertEquals(RouteLoadState.Loading, viewModel.uiState.value.loadState)
            advanceUntilIdle()
            assertEquals(3, fake.routeRequests.last().second.difficulty)
            assertEquals(null, fake.routeRequests.last().second.sectorId)
        }

    @Test
    fun dateChanges_resetsFloorAndAllSelections() =
        runTest(dispatcher) {
            viewModel.setSelectedDate(LocalDate.of(2026, 8, 11))
            viewModel.setCragInfo(1, "Synthetic gym")
            advanceUntilIdle()
            viewModel.selectFloor(2)
            advanceUntilIdle()
            val level = viewModel.uiState.value.gymLevelList.first()
            level.onClickListener(level)
            advanceUntilIdle()
            val sector = viewModel.uiState.value.sectorNameList.single()
            sector.onClickListener(sector)
            advanceUntilIdle()
            val route = viewModel.uiState.value.routeList.single()
            route.onClickListener(route)
            assertEquals(201L, viewModel.uiState.value.selectedSector.sectorId)
            assertEquals(1L, viewModel.uiState.value.selectedRoute.routeId)
            viewModel.setSelectedDate(LocalDate.of(2026, 8, 12))

            assertEquals(1, viewModel.uiState.value.curFloor)
            assertEquals(-1, viewModel.uiState.value.selectedLevel.difficulty)
            assertEquals(-1L, viewModel.uiState.value.selectedSector.sectorId)
            assertEquals(-1L, viewModel.uiState.value.selectedRoute.routeId)
            assertTrue(viewModel.uiState.value.routeList.isEmpty())
            advanceUntilIdle()
            assertEquals(GetGymRouteInfoRequest(0, 10, 1, timePoint = "2026-08-12"), fake.routeRequests.last().second)
            assertEquals(listOf(1L to "2026-08-11", 1L to "2026-08-12"), fake.keyRequests)
        }

    @Test
    fun middlePageFails_doesNotPublishPartialContentAndAllowsRetry() =
        runTest(dispatcher) {
            fake.routes = { request ->
                if (request.page == 0) {
                    BaseState.Success(GetGymRouteInfoResponse(0, true, listOf(route(1))))
                } else {
                    delay(800)
                    runRemote { Response.error(503, "<html>unavailable</html>".toResponseBody()) }
                }
            }
            viewModel.setCragInfo(1, "Synthetic gym")
            runCurrent()
            assertEquals(RouteLoadState.Loading, viewModel.uiState.value.loadState)
            assertTrue(viewModel.uiState.value.routeList.isEmpty())
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.loadState is RouteLoadState.RetryableError)
            assertTrue(viewModel.uiState.value.routeList.isEmpty())
            assertEquals(listOf(0, 1), fake.routeRequests.map { it.second.page })
            fake.routes = { BaseState.Success(GetGymRouteInfoResponse(0, false, listOf(route(2)))) }
            viewModel.retry()
            advanceUntilIdle()
            assertEquals(RouteLoadState.Content, viewModel.uiState.value.loadState)
            assertEquals(listOf(2L), viewModel.uiState.value.routeList.map { it.routeId })
            assertEquals(listOf(0, 1, 0), fake.routeRequests.map { it.second.page })
        }

    @Test
    fun duplicateIdentity_acrossPages_isTerminalContractFailure() =
        runTest(dispatcher) {
            fake.routes = { request ->
                BaseState.Success(GetGymRouteInfoResponse(request.page, request.page == 0, listOf(route(1))))
            }
            viewModel.setCragInfo(1, "Synthetic gym")
            advanceUntilIdle()

            val error = viewModel.uiState.value.loadState as RouteLoadState.TerminalError
            assertEquals(ErrorType.CONTRACT, error.error.type)
            assertTrue(viewModel.uiState.value.routeList.isEmpty())
        }

    @Test
    fun emptyResult_loads_asEmptyNotError() =
        runTest(dispatcher) {
            fake.routes = { BaseState.Success(GetGymRouteInfoResponse(0, false, emptyList())) }
            viewModel.setCragInfo(1, "Synthetic gym")
            advanceUntilIdle()

            assertEquals(RouteLoadState.Empty, viewModel.uiState.value.loadState)
        }

    @Test
    fun newQuery_cancelsOldLoad_withoutErrorState() =
        runTest(dispatcher) {
            viewModel.setCragInfo(1, "Synthetic gym")
            advanceUntilIdle()
            var cancelled = false
            fake.routes = { request ->
                if (request.floor == 2) {
                    try {
                        delay(800)
                    } catch (e: CancellationException) {
                        cancelled = true
                        throw e
                    }
                }
                BaseState.Success(GetGymRouteInfoResponse(0, false, listOf(route(1))))
            }
            viewModel.selectFloor(2)
            runCurrent()
            viewModel.selectFloor(1)
            advanceUntilIdle()

            assertTrue(cancelled)
            assertEquals(RouteLoadState.Content, viewModel.uiState.value.loadState)
        }

    @Test
    fun latestError_loadFails_preservesExistingToastConsumer() =
        runTest(dispatcher) {
            val events = mutableListOf<GymProfileRouteEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.event.collect(events::add) }
            fake.routes = { runRemote { Response.error(403, "".toResponseBody()) } }
            viewModel.setCragInfo(1, "Synthetic gym")
            advanceUntilIdle()

            assertEquals(listOf(GymProfileRouteEvent.ShowToastMessage("네트워크 통신 에러")), events)
            assertTrue(viewModel.uiState.value.loadState is RouteLoadState.TerminalError)
            viewModel.retry()
            advanceUntilIdle()
            assertEquals(1, fake.routeRequests.size)
        }

    @Test
    fun staleFilteringKey_afterGymChange_doesNotStartOldRoutes() =
        runTest(dispatcher) {
            fake.delayFirstGymKey = true
            viewModel.setCragInfo(1, "Old gym")
            runCurrent()
            viewModel.setCragInfo(2, "New gym")
            advanceUntilIdle()

            assertEquals(listOf(2L), fake.routeRequests.map { it.first })
            assertEquals(RouteLoadState.Content, viewModel.uiState.value.loadState)
        }

    @Test
    fun staleFilteringKeyFailure_afterGymChange_keepsNewContent() =
        runTest(dispatcher) {
            fake.delayFirstGymKey = true
            fake.failFirstGymKey = true
            viewModel.setCragInfo(1, "Old gym")
            runCurrent()
            viewModel.setCragInfo(2, "New gym")
            advanceUntilIdle()

            assertEquals(listOf(2L), fake.routeRequests.map { it.first })
            assertEquals(RouteLoadState.Content, viewModel.uiState.value.loadState)
        }

    @Test
    fun legacyV0_loadAndSelect_displaysVbWithoutChangingDifficulty() =
        runTest(dispatcher) {
            fake.difficulties = listOf(DifficultyItem("V0", 0, "#FFFFFF", "V0"))
            fake.routes = {
                BaseState.Success(
                    GetGymRouteInfoResponse(
                        0,
                        false,
                        listOf(route(1).copy(climeetDifficultyName = "V0", gymDifficultyName = "V0", difficulty = 0)),
                    ),
                )
            }
            viewModel.setCragInfo(1, "Synthetic gym")
            advanceUntilIdle()
            val level = viewModel.uiState.value.gymLevelList.single()
            assertEquals("VB", level.levelName)
            assertEquals("#FFFFFF", level.levelColor)
            level.onClickListener(level)
            advanceUntilIdle()
            assertEquals(0, fake.routeRequests.last().second.difficulty)
            val item = viewModel.uiState.value.routeList.single()
            assertEquals("VB", item.gymLevelName)
            assertEquals("VB", item.climeetLevelName)
            item.onClickListener(item)
            assertEquals("VB", viewModel.uiState.value.selectedRoute.gymLevelName)
        }

    @Test
    fun cancellationResumesOldFailureImmediately_newQuery_neverPublishesOldError() =
        runTest(dispatcher) {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val states = mutableListOf<RouteLoadState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect { states.add(it.loadState) }
            }
            viewModel.setCragInfo(1, "Synthetic gym")
            fake.routes = { request ->
                if (request.floor == 2) {
                    try {
                        delay(800)
                    } catch (_: CancellationException) {
                        // 취소에 협조하지 않는 경계가 즉시 실패를 반환하는 경우를 재현합니다.
                    }
                    BaseState.Error("old failure", "500")
                } else {
                    BaseState.Success(GetGymRouteInfoResponse(0, false, listOf(route(1))))
                }
            }
            viewModel.selectFloor(2)
            viewModel.selectFloor(1)
            advanceUntilIdle()

            assertTrue(states.none { it is RouteLoadState.TerminalError || it is RouteLoadState.RetryableError })
            assertEquals(RouteLoadState.Content, viewModel.uiState.value.loadState)
        }

    private class RouteRepositoryFake {
        val repository = mockk<MainRepository>()
        val keyRequests = mutableListOf<Pair<Long, String>>()
        val routeRequests = mutableListOf<Pair<Long, GetGymRouteInfoRequest>>()
        var delayFirstGymKey = false
        var failFirstGymKey = false
        var difficulties = listOf(DifficultyItem("V3", 3, "#FDDA16", "Synthetic yellow"))
        var routes: suspend (GetGymRouteInfoRequest) -> BaseState<GetGymRouteInfoResponse> = {
            BaseState.Success(GetGymRouteInfoResponse(it.page, false, listOf(route(1))))
        }

        init {
            coEvery { repository.getGymFilteringKeyTime(any(), any()) } coAnswers {
                val gymId = firstArg<Long>()
                val date = secondArg<String>()
                keyRequests += gymId to date
                if (gymId == 1L && delayFirstGymKey) {
                    withContext(NonCancellable) { delay(800) }
                }
                if (gymId == 1L && failFirstGymKey) {
                    return@coAnswers BaseState.Error("old key error", "500")
                }
                BaseState.Success(
                    GetGymFilteringKeyResponse(
                        difficultyList = difficulties,
                        maxFloor = 2,
                        gymId = gymId.toInt(),
                        layoutImageUrl = "layout.png",
                        sectorList = listOf(
                            SectorItem(101, 1, "Sector A", "a.png"),
                            SectorItem(201, 2, "Sector B", "b.png"),
                        ),
                        timePoint = date,
                    ),
                )
            }
            coEvery { repository.getGymRouteInfoList(any(), any()) } coAnswers {
                val request = secondArg<GetGymRouteInfoRequest>()
                routeRequests += firstArg<Long>() to request
                routes(request)
            }
        }
    }

    companion object {
        private fun route(id: Long) =
            RouteItem(id, 101, "Sector A", "V3", 3, "Synthetic yellow", "#FDDA16", "$id.png", "노랑")
    }
}
