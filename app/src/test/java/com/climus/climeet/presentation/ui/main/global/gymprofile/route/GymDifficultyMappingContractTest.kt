package com.climus.climeet.presentation.ui.main.global.gymprofile.route

import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.model.request.GetGymRouteInfoRequest
import com.climus.climeet.data.model.response.DifficultyItem
import com.climus.climeet.data.model.response.GetGymFilteringKeyResponse
import com.climus.climeet.data.model.response.GetGymRouteInfoResponse
import com.climus.climeet.data.model.response.RouteItem
import com.climus.climeet.data.repository.MainRepository
import com.climus.climeet.presentation.ui.main.mypage.myprofile.admin.model.LevelColorData
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@OptIn(ExperimentalCoroutinesApi::class)
class GymDifficultyMappingContractTest(
    private val names: String,
    private val colorNames: String,
) {
    @Test
    fun approvedDifficultySequence_loadAndSelect_preservesDisplayAndRequestIdentity() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val expectedNames = names.split(" ")
                val expectedColors = colorNames.split(" ").map { name ->
                    LevelColorData.COLORS.single { it.name == name }.color
                }
                val contract = expectedNames.mapIndexed { difficulty, name ->
                    DifficultyItem(name, difficulty, expectedColors[difficulty], name)
                }
                val requests = mutableListOf<GetGymRouteInfoRequest>()
                val repository = mockk<MainRepository>()
                coEvery { repository.getGymFilteringKeyTime(any(), any()) } coAnswers {
                    BaseState.Success(GetGymFilteringKeyResponse(contract, 1, 1, "", emptyList(), secondArg()))
                }
                coEvery { repository.getGymRouteInfoList(1, any()) } coAnswers {
                    val request = secondArg<GetGymRouteInfoRequest>()
                    requests += request
                    val routes = contract.filter { request.difficulty == null || it.difficulty == request.difficulty }
                        .map {
                            RouteItem(
                                it.difficulty.toLong(),
                                1,
                                "Sector",
                                it.climeetDifficultyName,
                                it.difficulty,
                                it.gymDifficultyName,
                                it.gymDifficultyColor,
                                "route.png",
                                "하양",
                            )
                        }
                    val page = routes.drop(request.page * request.size).take(request.size)
                    val hasNext = (request.page + 1) * request.size < routes.size
                    BaseState.Success(GetGymRouteInfoResponse(request.page, hasNext, page))
                }
                val viewModel = GymProfileRouteViewModel(repository)
                viewModel.setCragInfo(1, "Synthetic gym")
                advanceUntilIdle()
                val filters = viewModel.uiState.value.gymLevelList

                assertEquals(expectedNames, filters.map { it.levelName })
                assertEquals(expectedColors, filters.map { it.levelColor })
                assertEquals(expectedNames.indices.toList(), filters.map { it.difficulty })
                assertEquals(expectedNames, viewModel.uiState.value.routeList.map { it.climeetLevelName })
                for ((difficulty, filter) in filters.withIndex()) {
                    filter.onClickListener(filter)
                    advanceUntilIdle()
                    assertEquals(difficulty, requests.last().difficulty)
                    val selected = viewModel.uiState.value.gymLevelList.filter { it.isSelected }
                    assertEquals(listOf(difficulty), selected.map { it.difficulty })
                    val route = viewModel.uiState.value.routeList.single()
                    assertEquals(expectedNames[difficulty], route.gymLevelName)
                    assertEquals(expectedNames[difficulty], route.climeetLevelName)
                }
                assertEquals(expectedNames.indices.toList(), requests.mapNotNull { it.difficulty })
            } finally {
                Dispatchers.resetMain()
            }
        }

    companion object {
        // 사용자 제공 5~13단계 사례와 확정된 VB·C1~C3 표기를 각각 기록합니다.
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<String>> =
            listOf(
                arrayOf("VB V1 V2 V3 V4", "하양 빨강 주황 노랑 초록"),
                arrayOf("VB V1 V2 V3 V4 V5", "하양 빨강 주황 노랑 초록 파랑"),
                arrayOf("VB V1 V2 V3 V4 V5 V6", "하양 빨강 주황 노랑 초록 파랑 남색"),
                arrayOf("VB V1 V2 V3 V4 V5 V6 V7", "하양 빨강 주황 노랑 초록 파랑 남색 보라"),
                arrayOf("VB V1 V2 V3 V4 V5 V6 V7 V8", "하양 빨강 주황 노랑 초록 파랑 남색 보라 갈색"),
                arrayOf("VB V1 V2 V3 V4 V5 V6 V7 V8 V9", "하양 빨강 주황 노랑 초록 파랑 남색 보라 갈색 회색"),
                arrayOf("VB V1 V2 V3 V4 V5 V6 V7 V8 V9 C1", "하양 빨강 주황 노랑 초록 파랑 남색 보라 갈색 회색 검정"),
                arrayOf("VB V1 V2 V3 V4 V5 V6 V7 V8 V9 C1 C2", "하양 빨강 주황 노랑 초록 파랑 남색 보라 갈색 회색 검정 검정"),
                arrayOf("VB V1 V2 V3 V4 V5 V6 V7 V8 V9 C1 C2 C3", "하양 빨강 주황 노랑 초록 파랑 남색 보라 갈색 회색 검정 검정 검정"),
            )
    }
}
