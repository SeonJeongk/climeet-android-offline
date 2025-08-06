package com.climus.climeet.presentation.ui.main.record.session.record.submission

import com.climus.climeet.data.local.ClimbingRecordData
import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.repository.MainRepository
import com.climus.climeet.presentation.util.formatStopwatchTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SessionSubmissionState {
    data object Draft : SessionSubmissionState

    data object Submitting : SessionSubmissionState

    data class Preserved(
        val message: String,
    ) : SessionSubmissionState

    data object Succeeded : SessionSubmissionState
}

sealed interface SessionSubmissionEffect {
    data class ShowMessage(
        val message: String,
    ) : SessionSubmissionEffect

    data object NavigateToComplete : SessionSubmissionEffect

    data object DraftDiscarded : SessionSubmissionEffect
}

/**
 * 운동 기록을 수정하고 서버로 보내는 순서를 관리합니다.
 *
 * 한 번에 한 작업만 처리해 전송 중인 기록이 바뀌지 않게 합니다.
 * 전송에 성공하면 기기 기록을 삭제하고, 실패하면 다음 실행에서 복구할 수 있도록 남깁니다.
 */
internal class SessionSubmissionController(
    private val repository: MainRepository,
) {
    private val mutationMutex = Mutex()
    private val mutableState = MutableStateFlow<SessionSubmissionState>(SessionSubmissionState.Draft)
    val state: StateFlow<SessionSubmissionState> = mutableState.asStateFlow()

    private val effects = Channel<SessionSubmissionEffect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()

    suspend fun restoreDraft(): ClimbingRecordData? =
        mutationMutex.withLock {
            val draft = repository.getRecord(RECORD_ID) ?: return@withLock null
            mutableState.value = if (draft.time.isBlank()) {
                SessionSubmissionState.Draft
            } else {
                SessionSubmissionState.Preserved(PRESERVED_RECORD_MESSAGE)
            }
            draft
        }

    suspend fun submit(elapsedTimeMillis: Long) {
        mutationMutex.withLock {
            if (mutableState.value != SessionSubmissionState.Draft) return
            mutableState.value = SessionSubmissionState.Submitting
            var snapshotFinalized = false
            try {
                val request = repository.finalizeSessionRecord(
                    RECORD_ID,
                    formatStopwatchTime(elapsedTimeMillis, alwaysShowHours = true),
                ) ?: run {
                    mutableState.value = SessionSubmissionState.Draft
                    effects.send(SessionSubmissionEffect.ShowMessage("저장된 운동 기록을 찾지 못했습니다."))
                    return
                }
                snapshotFinalized = true
                when (val result = repository.createClimbingRecord(request)) {
                    is BaseState.Success -> {
                        repository.clearSessionDraft()
                        mutableState.value = SessionSubmissionState.Succeeded
                        effects.send(SessionSubmissionEffect.NavigateToComplete)
                    }

                    is BaseState.Error -> preserve(result.msg)
                }
            } catch (cancellation: CancellationException) {
                mutableState.value = if (snapshotFinalized) {
                    SessionSubmissionState.Preserved(PRESERVED_RECORD_MESSAGE)
                } else {
                    SessionSubmissionState.Draft
                }
                throw cancellation
            } catch (_: Exception) {
                if (snapshotFinalized) {
                    preserve(PRESERVED_RECORD_MESSAGE)
                } else {
                    mutableState.value = SessionSubmissionState.Draft
                    effects.send(SessionSubmissionEffect.ShowMessage("운동 기록을 저장하지 못했습니다."))
                }
            }
        }
    }

    suspend fun discardPreservedDraft(): Boolean =
        mutationMutex.withLock {
            if (mutableState.value !is SessionSubmissionState.Preserved) return@withLock false
            repository.clearSessionDraft()
            mutableState.value = SessionSubmissionState.Draft
            effects.send(SessionSubmissionEffect.DraftDiscarded)
            true
        }

    suspend fun acknowledgeSuccess() {
        mutationMutex.withLock {
            if (mutableState.value == SessionSubmissionState.Succeeded) {
                mutableState.value = SessionSubmissionState.Draft
            }
        }
    }

    suspend fun mutateDraft(block: suspend () -> Unit) {
        mutationMutex.withLock {
            if (mutableState.value == SessionSubmissionState.Draft) block()
        }
    }

    private suspend fun preserve(message: String) {
        mutableState.value = SessionSubmissionState.Preserved(message.ifBlank { PRESERVED_RECORD_MESSAGE })
        effects.send(SessionSubmissionEffect.ShowMessage(PRESERVED_RECORD_MESSAGE))
    }

    private companion object {
        const val RECORD_ID = 1
        const val PRESERVED_RECORD_MESSAGE = "기록 전송을 완료하지 못했습니다. 기록은 기기에 보관했습니다."
    }
}
