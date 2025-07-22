package com.climus.climeet.data.local

import kotlin.math.roundToInt

internal fun completedAverageDifficulty(routes: List<RouteRecordData>): Int? {
    val completedDifficulties = routes
        .asSequence()
        .filter { it.isCompleted && it.difficulty in CLIMBING_DIFFICULTY_RANGE }
        .map { it.difficulty }
        .toList()
    return completedDifficulties
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.roundToInt()
}

private val CLIMBING_DIFFICULTY_RANGE = 0..12
