package com.climus.climeet.presentation.ui.main.record.timer.setrecord

import com.climus.climeet.data.local.RouteRecordData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.RouteUiData
import com.climus.climeet.presentation.util.formatTimerTime
import com.climus.climeet.presentation.util.parseTimerTime

internal data class RecoveredTimer(
    val elapsedTimeMillis: Long,
    val displayDuration: String,
)

internal data class TimerDraftSummary(
    val routeCount: String,
    val completedCount: String,
)

internal fun recoverTimer(
    storedDuration: String,
    pausedElapsedTimeMillis: Long,
): RecoveredTimer? {
    val elapsedTime = if (storedDuration.isBlank()) {
        pausedElapsedTimeMillis.takeIf { it > 0L }
    } else {
        parseTimerTime(storedDuration)
    } ?: return null
    return RecoveredTimer(elapsedTime, formatTimerTime(elapsedTime))
}

internal fun summarizeTimerDraft(routes: List<RouteRecordData>): TimerDraftSummary =
    TimerDraftSummary(
        routeCount = routes.size.takeIf { it > 0 }?.toString() ?: "--",
        completedCount = routes.count(RouteRecordData::isCompleted).takeIf { routes.isNotEmpty() }?.toString() ?: "--",
    )

internal fun restoreRouteItems(
    records: List<RouteRecordData>,
    catalogRoutes: List<RouteUiData>,
): List<RouteUiData> =
    records.mapNotNull { record ->
        catalogRoutes.firstOrNull { route ->
            route.routeId == record.routeId && route.sectorId == record.sectorId
        }?.copy(
            clearBtnState = record.isCompleted,
            challengeNum = record.attemptCount,
            isSelected = true,
        )
    }
