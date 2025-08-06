package com.climus.climeet.presentation.ui.main.record.session.record.policy

import com.climus.climeet.data.local.RouteRecordData
import com.climus.climeet.data.local.completedAverageDifficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionDifficultyPolicyTest {
    @Test
    fun incompleteAndLegacyRoutes_calculateAverage_usesCompletedZeroToTwelveOnly() {
        val routes = listOf(
            route(difficulty = 12, completed = true),
            route(difficulty = 10, completed = true),
            route(difficulty = 1, completed = false),
            route(difficulty = -1, completed = true),
            route(difficulty = 13, completed = true),
        )

        assertEquals(11, completedAverageDifficulty(routes))
    }

    @Test
    fun noCompletedRoutes_calculateAverage_returnsNull() {
        assertNull(completedAverageDifficulty(listOf(route(difficulty = 7, completed = false))))
    }

    @Test
    fun missingDifficulty_findClosestLevel_usesNearestActualKey() {
        val levels = listOf(0 to "VB", 3 to "V2", 7 to "V6")

        assertEquals("V2", closestLevelName(4, levels))
    }

    @Test
    fun equalDistance_findClosestLevel_usesLowerDifficulty() {
        val levels = listOf(3 to "V2", 5 to "V4")

        assertEquals("V2", closestLevelName(4, levels))
    }

    @Test
    fun missingAverage_findClosestLevel_returnsNull() {
        assertNull(closestLevelName(null, listOf(0 to "VB")))
    }

    private fun route(
        difficulty: Int,
        completed: Boolean,
    ) = RouteRecordData(
        routeId = difficulty.toLong(),
        difficulty = difficulty,
        isCompleted = completed,
    )
}
