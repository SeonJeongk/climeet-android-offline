package com.climus.climeet.presentation.ui.main.record.session.record.draft

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

/**
 * draft에 저장된 시간으로 스톱워치 표시값을 복원합니다.
 *
 * 종료 시간이 저장돼 있으면 해당 값을 우선합니다. 비어 있을 때만 일시정지 시점의 경과 시간을 사용합니다.
 * 두 값 모두 유효하지 않으면 복원하지 않습니다.
 */
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

/** route 기록이 없으면 개수 대신 화면의 빈 값인 `--`를 반환합니다. */
internal fun summarizeSessionDraft(routes: List<RouteRecordData>): SessionDraftSummary =
    SessionDraftSummary(
        routeCount = routes.size.takeIf { it > 0 }?.toString() ?: "--",
        completedCount = routes.count(RouteRecordData::isCompleted).takeIf { routes.isNotEmpty() }?.toString() ?: "--",
    )

/**
 * 저장된 route를 현재 암장 catalog의 화면 항목에 연결합니다.
 *
 * sector ID와 route ID가 모두 일치하는 항목만 복원합니다. 현재 catalog에 없는 route는 화면에 표시하지 않습니다.
 */
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
