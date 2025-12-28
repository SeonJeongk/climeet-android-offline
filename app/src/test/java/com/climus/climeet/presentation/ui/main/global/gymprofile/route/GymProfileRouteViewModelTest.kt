package com.climus.climeet.presentation.ui.main.global.gymprofile.route

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.model.ErrorType
import com.climus.climeet.data.repository.route.CachedRoute
import com.climus.climeet.data.repository.route.Freshness
import com.climus.climeet.data.repository.route.RouteCatalog
import com.climus.climeet.data.repository.route.RouteCatalogKey
import com.climus.climeet.data.repository.route.RouteCatalogRepository
import com.climus.climeet.data.repository.route.RouteCatalogState
import com.climus.climeet.data.repository.route.RouteDifficulty
import com.climus.climeet.data.repository.route.RouteSector
import com.climus.climeet.presentation.ui.main.global.selectsector.model.RouteUiData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class GymProfileRouteViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeRouteCatalogRepository
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
        repository = FakeRouteCatalogRepository()
        viewModel = GymProfileRouteViewModel(repository)
    }

    @After
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
    }

    @Test
    fun historicalDate_setCragInfo_usesDateInCatalogKey() =
        runTest(dispatcher) {
            val date = LocalDate.parse("2026-08-12")
            viewModel.setSelectedDate(date)
            viewModel.setCragInfo(1L, "Synthetic gym")
            advanceUntilIdle()

            assertEquals(RouteCatalogKey(1L, date), repository.observations.single())
        }

    @Test
    fun localFilters_selectValues_filterWithoutNewObservation() =
        runTest(dispatcher) {
            viewModel.setSelectedDate(LocalDate.parse("2026-08-12"))
            viewModel.setCragInfo(1L, "Synthetic gym")
            advanceUntilIdle()
            viewModel.selectFloor(2)
            advanceUntilIdle()
            viewModel.uiState.value.sectorNameList.single().let { it.onClickListener(it) }
            advanceUntilIdle()
            viewModel.uiState.value.gymLevelList.single().let { it.onClickListener(it) }
            advanceUntilIdle()

            assertEquals(1, repository.observations.size)
            assertEquals(2, viewModel.uiState.value.curFloor)
            assertEquals(listOf(2L), viewModel.uiState.value.routeList.map { it.routeId })
        }

    @Test
    fun rapidFloorChanges_selectFloor_keepsFinalFilterWithoutNetwork() =
        runTest(dispatcher) {
            viewModel.setSelectedDate(LocalDate.parse("2026-08-12"))
            viewModel.setCragInfo(1L, "Synthetic gym")
            advanceUntilIdle()

            repeat(50) { index ->
                viewModel.selectFloor(if (index % 2 == 0) 2 else 1)
            }
            advanceUntilIdle()

            assertEquals(1, repository.observations.size)
            assertEquals(1, viewModel.uiState.value.curFloor)
            assertEquals(listOf(1L), viewModel.uiState.value.routeList.map { it.routeId })
        }

    @Test
    fun missingCatalog_failed_showsRetryAndRetryObservesAgain() =
        runTest(dispatcher) {
            repository.failure = BaseState.Error("offline", "TRANSPORT", type = ErrorType.TRANSPORT)
            viewModel.setCragInfo(1L, "Synthetic gym")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.loadState is RouteLoadState.RetryableError)

            repository.failure = null
            viewModel.retry()
            advanceUntilIdle()

            assertEquals(2, repository.observations.size)
            assertEquals(RouteLoadState.Content, viewModel.uiState.value.loadState)
        }

    @Test
    fun selectedRouteRemoved_catalogReplaced_clearsSelection() =
        runTest(dispatcher) {
            viewModel.setCragInfo(1L, "Synthetic gym")
            advanceUntilIdle()
            viewModel.uiState.value.routeList.first().let { it.onClickListener(it) }
            assertEquals(1L, viewModel.uiState.value.selectedRoute.routeId)

            val replacement = listOf(CachedRoute(9, 101, "A", "V3", 3, "V3", "#fff", "", "하양"))
            repository.replaceCatalog(routeCatalog(routes = replacement))
            advanceUntilIdle()

            assertEquals(RouteUiData {}.routeId, viewModel.uiState.value.selectedRoute.routeId)
            assertEquals(listOf(9L), viewModel.uiState.value.routeList.map { it.routeId })
        }

    @Test
    fun selectedRouteKept_catalogReplaced_keepsSelection() =
        runTest(dispatcher) {
            viewModel.setCragInfo(1L, "Synthetic gym")
            advanceUntilIdle()
            viewModel.uiState.value.routeList.first().let { it.onClickListener(it) }

            repository.replaceCatalog(routeCatalog())
            advanceUntilIdle()

            assertEquals(1L, viewModel.uiState.value.selectedRoute.routeId)
        }

    @Test
    fun zeroDifficulty_observe_showsVbOnLevelAndRoute() =
        runTest(dispatcher) {
            repository.catalog = routeCatalog(
                difficulties = listOf(RouteDifficulty(0, "V0", "V0", "#fff")),
                routes = listOf(CachedRoute(1, 101, "A", "V0", 0, "V0", "#fff", "", "하양")),
            )
            viewModel.setCragInfo(1L, "Synthetic gym")
            advanceUntilIdle()

            assertEquals("VB", viewModel.uiState.value.gymLevelList.single().levelName)
            assertEquals("VB", viewModel.uiState.value.routeList.single().gymLevelName)
            assertEquals("VB", viewModel.uiState.value.routeList.single().climeetLevelName)
        }

    @Test
    fun staleCatalog_refreshing_keepsContentAndShowsIndicator() =
        runTest(dispatcher) {
            repository.isRefreshing = true
            viewModel.setCragInfo(1L, "Synthetic gym")
            advanceUntilIdle()

            assertEquals(listOf(1L), viewModel.uiState.value.routeList.map { it.routeId })
            assertEquals(RouteLoadState.Content, viewModel.uiState.value.loadState)
            assertTrue(viewModel.uiState.value.isRefreshing)
        }

    @Test
    fun invalidGym_setCragInfo_skipsCacheAccess() =
        runTest(dispatcher) {
            viewModel.setCragInfo(0L, "")
            advanceUntilIdle()

            assertTrue(repository.observations.isEmpty())
        }
}

internal class FakeRouteCatalogRepository(
    var catalog: RouteCatalog = routeCatalog(),
) : RouteCatalogRepository {
    private val catalogVersion = MutableStateFlow(0)
    val observations = mutableListOf<RouteCatalogKey>()
    var isRefreshing: Boolean = false
    var failure: BaseState.Error? = null

    override fun observe(key: RouteCatalogKey): Flow<RouteCatalogState> {
        observations += key
        val currentFailure = failure
        return catalogVersion.map {
            if (currentFailure != null) {
                RouteCatalogState.Failed(currentFailure)
            } else {
                RouteCatalogState.Content(catalog, Freshness.STALE, isRefreshing, 1L)
            }
        }
    }

    fun replaceCatalog(replacement: RouteCatalog) {
        catalog = replacement
        catalogVersion.value++
    }
}

internal fun routeCatalog(
    difficulties: List<RouteDifficulty> = listOf(RouteDifficulty(3, "V3", "V3", "#fff")),
    routes: List<CachedRoute> = listOf(
        CachedRoute(1, 101, "A", "V3", 3, "V3", "#fff", "", "하양"),
        CachedRoute(2, 201, "B", "V3", 3, "V3", "#fff", "", "하양"),
    ),
) = RouteCatalog(
    maxFloor = 2,
    layoutImageUrl = "layout.png",
    serverTimePoint = "2026-08-12",
    sectors = listOf(RouteSector(101, 1, "A", ""), RouteSector(201, 2, "B", "")),
    difficulties = difficulties,
    routes = routes,
)
