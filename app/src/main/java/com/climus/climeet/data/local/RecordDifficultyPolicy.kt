package com.climus.climeet.data.local

import kotlin.math.roundToInt

/**
 * 완등한 route 중 현재 난이도 범위인 0부터 12까지의 평균을 계산합니다.
 *
 * 평균은 가장 가까운 정수로 반올림합니다. 집계할 route가 없으면 `null`을 반환합니다.
 */
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
