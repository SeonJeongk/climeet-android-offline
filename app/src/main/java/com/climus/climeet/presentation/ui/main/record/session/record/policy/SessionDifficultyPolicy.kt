package com.climus.climeet.presentation.ui.main.record.session.record.policy

import kotlin.math.abs

/**
 * 평균 난이도와 가장 가까운 암장의 실제 레벨 이름을 찾습니다.
 *
 * 평균에서 같은 거리의 레벨이 둘이면 낮은 난이도를 선택합니다.
 * 평균이 없거나 암장 레벨 목록이 비어 있으면 `null`을 반환합니다.
 */
internal fun closestLevelName(
    averageDifficulty: Int?,
    levels: List<Pair<Int, String>>,
): String? =
    averageDifficulty?.let { average ->
        levels
            .distinctBy { it.first }
            .minWithOrNull(compareBy<Pair<Int, String>> { abs(it.first - average) }.thenBy { it.first })
            ?.second
    }
