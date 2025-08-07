package com.climus.climeet.presentation.ui.main.record.session.record.draft

import com.climus.climeet.data.local.ClimbingRecordData
import com.climus.climeet.data.local.RouteRecordData
import com.climus.climeet.data.repository.MainRepository
import com.climus.climeet.presentation.ui.main.global.selectsector.model.RouteUiData
import kotlin.math.roundToInt

/**
 * 수정 가능한 운동 draft와 선택한 route를 Room에 저장합니다.
 *
 * 종료 시간이 확정된 보존 기록은 새 운동으로 덮어쓰지 않습니다. route는 sector ID와 route ID 조합으로 구분하며,
 * 도전 횟수는 0보다 작아지지 않습니다.
 */
internal class SessionDraftStore(
    private val repository: MainRepository,
) {
    suspend fun saveClimbingDraft(record: ClimbingRecordData): Boolean {
        val existing = repository.getRecord(record.id)
        if (existing?.time?.isNotBlank() == true) return false
        repository.insert(record)
        return true
    }

    suspend fun saveRoute(item: RouteUiData): Boolean {
        if (repository.findExistRoute(item.sectorId, item.routeId) != null) return false
        repository.insert(
            RouteRecordData(
                sectorId = item.sectorId,
                routeId = item.routeId,
                levelName = item.gymLevelName,
                levelColor = item.gymLevelColor,
                difficulty = item.difficulty,
                attemptCount = item.challengeNum,
            ),
        )
        return true
    }

    suspend fun changeAttempt(
        item: RouteUiData,
        delta: Int,
    ): Boolean {
        val record = repository.findExistRoute(item.sectorId, item.routeId) ?: return false
        repository.update(record.copy(attemptCount = (record.attemptCount + delta).coerceAtLeast(0)))
        return true
    }

    suspend fun setCompleted(
        item: RouteUiData,
        completed: Boolean,
    ): Boolean {
        val record = repository.findExistRoute(item.sectorId, item.routeId) ?: return false
        repository.update(record.copy(isCompleted = completed))
        return true
    }

    suspend fun delete(item: RouteUiData): Boolean {
        val record = repository.findExistRoute(item.sectorId, item.routeId) ?: return false
        repository.deleteById(record.id)
        return true
    }

    suspend fun averageDifficulty(): Int? = repository.getAverageDifficultyOfCompleted()?.roundToInt()
}
