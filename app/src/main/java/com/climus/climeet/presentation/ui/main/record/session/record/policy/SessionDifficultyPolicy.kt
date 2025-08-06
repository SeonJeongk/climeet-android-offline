package com.climus.climeet.presentation.ui.main.record.session.record.policy

import kotlin.math.abs

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
