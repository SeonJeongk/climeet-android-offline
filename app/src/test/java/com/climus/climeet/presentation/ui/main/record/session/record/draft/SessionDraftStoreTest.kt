package com.climus.climeet.presentation.ui.main.record.session.record

import com.climus.climeet.data.local.ClimbingRecordData
import com.climus.climeet.data.local.RouteRecordData
import com.climus.climeet.data.repository.MainRepository
import com.climus.climeet.presentation.ui.main.global.selectsector.model.RouteUiData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDraftStoreTest {
    private val repository = mockk<MainRepository>(relaxed = true)
    private val store = SessionDraftStore(repository)

    @Test
    fun finalizedDraft_saveClimbingDraft_doesNotOverwritePreservedRecord() =
        runTest {
            val preserved = ClimbingRecordData(id = 1, gymId = 1, time = "00:00:03")
            coEvery { repository.getRecord(1) } returns preserved

            val saved = store.saveClimbingDraft(preserved.copy(gymId = 2, time = ""))

            assertFalse(saved)
            coVerify(exactly = 0) { repository.insert(any<ClimbingRecordData>()) }
        }

    @Test
    fun selectedRoute_saveRoute_persistsVisibleInitialAttemptCount() =
        runTest {
            val route = RouteUiData(routeId = 10, sectorId = 20, challengeNum = 1) {}
            coEvery { repository.findExistRoute(20, 10) } returns null

            val saved = store.saveRoute(route)

            assertTrue(saved)
            coVerify(exactly = 1) {
                repository.insert(
                    match<RouteRecordData> {
                        it.routeId == 10L && it.sectorId == 20L && it.attemptCount == 1
                    },
                )
            }
        }
}
