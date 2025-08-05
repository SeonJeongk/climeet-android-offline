package com.climus.climeet.presentation.ui.main.record.session.record

import com.climus.climeet.data.local.RouteRecordData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.RouteUiData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDraftRecoveryTest {
    @Test
    fun savedRoutes_restore_preservesCountsCompletionAndAttempt() {
        val records = listOf(
            RouteRecordData(routeId = 10, sectorId = 1, attemptCount = 3, isCompleted = true),
            RouteRecordData(routeId = 20, sectorId = 1, attemptCount = 2, isCompleted = false),
        )
        val catalog = listOf(
            RouteUiData(routeId = 10, sectorId = 1) {},
            RouteUiData(routeId = 20, sectorId = 1) {},
        )

        val summary = summarizeSessionDraft(records)
        val restoredRoutes = restoreRouteItems(records, catalog)

        assertEquals("2", summary.routeCount)
        assertEquals("1", summary.completedCount)
        assertEquals(3, restoredRoutes.first().challengeNum)
        assertTrue(restoredRoutes.first().clearBtnState)
        assertFalse(restoredRoutes.last().clearBtnState)
    }
}
