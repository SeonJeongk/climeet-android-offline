package com.climus.climeet.presentation.ui.main.record.session.record

import com.climus.climeet.data.local.RouteRecordData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.RouteUiData
import com.climus.climeet.presentation.util.formatStopwatchTime
import com.climus.climeet.presentation.util.parseStopwatchTime

internal data class RecoveredStopwatch(
    val elapsedTimeMillis: Long,
    val displayDuration: String,
)

internal data class SessionDraftSummary(
    val routeCount: String,
    val completedCount: String,
)

internal fun recoverStopwatch(
    storedDuration: String,
    pausedElapsedTimeMillis: Long,
): RecoveredStopwatch? {
    val elapsedTime = if (storedDuration.isBlank()) {
        pausedElapsedTimeMillis.takeIf { it > 0L }
    } else {
        parseStopwatchTime(storedDuration)
    } ?: return null
    return RecoveredStopwatch(elapsedTime, formatStopwatchTime(elapsedTime))
}

internal fun summarizeSessionDraft(routes: List<RouteRecordData>): SessionDraftSummary =
    SessionDraftSummary(
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
