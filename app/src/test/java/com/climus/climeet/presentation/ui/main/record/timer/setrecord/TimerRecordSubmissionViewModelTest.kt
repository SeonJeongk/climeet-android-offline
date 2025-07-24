package com.climus.climeet.presentation.ui.main.record.timer.setrecord

import android.content.SharedPreferences
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.climus.climeet.app.App
import com.climus.climeet.data.local.ClimbingRecordData
import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.model.request.CreateTimerClimbingRecordRequest
import com.climus.climeet.data.model.response.GetGymFilteringKeyResponse
import com.climus.climeet.data.model.response.GetGymRouteInfoResponse
import com.climus.climeet.data.repository.MainRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerRecordSubmissionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<MainRepository>(relaxed = true)
    private val request = CreateTimerClimbingRecordRequest(1, "2026-09-21", "01:02:03", 5, emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        ArchTaskExecutor.getInstance().setDelegate(
            object : TaskExecutor() {
                override fun executeOnDiskIO(runnable: Runnable) = runnable.run()

                override fun postToMainThread(runnable: Runnable) = runnable.run()

                override fun isMainThread() = true
            },
        )
        App.sharedPreferences = mockk<SharedPreferences>(relaxed = true).also { preferences ->
            every { preferences.getString(any(), any()) } answers { secondArg() }
        }
        coEvery { repository.getRecord(1) } returns null
    }

    @After
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun successfulPost_submit_clearsDraftAndNavigatesOnce() =
        runTest(dispatcher) {
            coEvery { repository.finalizeTimerClimbingRecord(1, "01:02:03") } returns request
            coEvery { repository.createTimerClimbingRecord(request) } returns BaseState.Success("".toResponseBody())
            val viewModel = SetTimerClimbingRecordViewModel(repository)
            val effects = mutableListOf<TimerSubmissionEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.submissionEffect.collect(effects::add)
            }
            advanceUntilIdle()

            viewModel.submitRecord(3_723_000L)
            advanceUntilIdle()

            assertEquals(TimerSubmissionState.Succeeded, viewModel.submissionState.value)
            assertEquals(listOf(TimerSubmissionEffect.NavigateToComplete), effects)
            coVerify(exactly = 1) { repository.clearTimerClimbingRecordDraft() }

            viewModel.acknowledgeSubmissionSuccess()
            advanceUntilIdle()
            assertEquals(TimerSubmissionState.Draft, viewModel.submissionState.value)
        }

    @Test
    fun failedPost_submit_preservesDraftAndBlocksSecondPost() =
        runTest(dispatcher) {
            coEvery { repository.finalizeTimerClimbingRecord(1, "01:02:03") } returns request
            coEvery { repository.createTimerClimbingRecord(request) } returns BaseState.Error("server error", "500")
            val viewModel = SetTimerClimbingRecordViewModel(repository)
            advanceUntilIdle()

            viewModel.submitRecord(3_723_000L)
            advanceUntilIdle()
            viewModel.submitRecord(3_723_000L)
            advanceUntilIdle()

            assertTrue(viewModel.submissionState.value is TimerSubmissionState.Preserved)
            coVerify(exactly = 1) { repository.createTimerClimbingRecord(request) }
            coVerify(exactly = 0) { repository.clearTimerClimbingRecordDraft() }
        }

    @Test
    fun duplicateInput_whileSubmitting_sendsSinglePost() =
        runTest(dispatcher) {
            coEvery { repository.finalizeTimerClimbingRecord(1, "01:02:03") } returns request
            coEvery { repository.createTimerClimbingRecord(request) } coAnswers {
                delay(800)
                BaseState.Success("".toResponseBody())
            }
            val viewModel = SetTimerClimbingRecordViewModel(repository)
            advanceUntilIdle()

            repeat(10) { viewModel.submitRecord(3_723_000L) }
            runCurrent()
            assertEquals(TimerSubmissionState.Submitting, viewModel.submissionState.value)
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.createTimerClimbingRecord(request) }
        }

    @Test
    fun pendingDraftMutation_submit_waitsForLastWriteBeforeFinalizingSnapshot() =
        runTest(dispatcher) {
            val mutationStarted = CompletableDeferred<Unit>()
            val releaseMutation = CompletableDeferred<Unit>()
            val order = mutableListOf<String>()
            coEvery { repository.finalizeTimerClimbingRecord(1, "01:02:03") } coAnswers {
                order += "finalize"
                request
            }
            coEvery { repository.createTimerClimbingRecord(request) } returns BaseState.Success("".toResponseBody())
            val controller = TimerRecordSubmissionController(repository)

            val mutation = launch {
                controller.mutateDraft {
                    order += "mutation-start"
                    mutationStarted.complete(Unit)
                    releaseMutation.await()
                    order += "mutation-end"
                }
            }
            mutationStarted.await()
            val submission = launch { controller.submit(3_723_000L) }
            runCurrent()

            coVerify(exactly = 0) { repository.finalizeTimerClimbingRecord(any(), any()) }
            releaseMutation.complete(Unit)
            mutation.join()
            submission.join()

            assertEquals(listOf("mutation-start", "mutation-end", "finalize"), order)
            coVerify(exactly = 1) { repository.createTimerClimbingRecord(request) }
        }

    @Test
    fun failedPost_recreatedViewModel_restoresPreservedDraftWithoutResending() =
        runTest(dispatcher) {
            coEvery { repository.finalizeTimerClimbingRecord(1, "01:02:03") } returns request
            coEvery { repository.createTimerClimbingRecord(request) } returns BaseState.Error("server error", "500")
            val firstViewModel = SetTimerClimbingRecordViewModel(repository)
            advanceUntilIdle()

            firstViewModel.submitRecord(3_723_000L)
            advanceUntilIdle()
            coEvery { repository.getRecord(1) } returns savedRecord(time = "01:02:03")

            val recreatedViewModel = SetTimerClimbingRecordViewModel(repository)
            advanceUntilIdle()
            recreatedViewModel.submitRecord(3_723_000L)
            advanceUntilIdle()

            assertTrue(recreatedViewModel.submissionState.value is TimerSubmissionState.Preserved)
            coVerify(exactly = 1) { repository.createTimerClimbingRecord(request) }
        }

    @Test
    fun finalizedDraft_restore_formatsDurationForTimerDisplay() =
        runTest(dispatcher) {
            coEvery { repository.getRecord(1) } returns savedRecord(time = "00:01:19")

            val viewModel = SetTimerClimbingRecordViewModel(repository)
            advanceUntilIdle()

            assertEquals(79_000L, viewModel.restoredElapsedTimeMillis.value)
            assertEquals("01:19", viewModel.restoredDuration.value)
        }

    @Test
    fun pausedDraft_restore_usesPersistedElapsedTime() =
        runTest(dispatcher) {
            coEvery { repository.getRecord(1) } returns savedRecord(time = "")
            every { App.sharedPreferences.getLong("pauseTime", 0L) } returns 79_000L

            val viewModel = SetTimerClimbingRecordViewModel(repository)
            advanceUntilIdle()

            assertEquals(TimerSubmissionState.Draft, viewModel.submissionState.value)
            assertEquals(79_000L, viewModel.restoredElapsedTimeMillis.value)
            assertEquals("01:19", viewModel.restoredDuration.value)
        }

    @Test
    fun localSnapshotFailure_submit_returnsToDraftWithoutPosting() =
        runTest(dispatcher) {
            coEvery { repository.finalizeTimerClimbingRecord(1, "01:02:03") } throws IllegalStateException("disk")
            val viewModel = SetTimerClimbingRecordViewModel(repository)
            advanceUntilIdle()

            viewModel.submitRecord(3_723_000L)
            advanceUntilIdle()

            assertEquals(TimerSubmissionState.Draft, viewModel.submissionState.value)
            coVerify(exactly = 0) { repository.createTimerClimbingRecord(any()) }
            coVerify(exactly = 0) { repository.clearTimerClimbingRecordDraft() }
        }

    @Test
    fun localClearFailure_afterSuccessfulPost_preservesDraftWithoutNavigation() =
        runTest(dispatcher) {
            coEvery { repository.finalizeTimerClimbingRecord(1, "01:02:03") } returns request
            coEvery { repository.createTimerClimbingRecord(request) } returns BaseState.Success("".toResponseBody())
            coEvery { repository.clearTimerClimbingRecordDraft() } throws IllegalStateException("disk")
            val viewModel = SetTimerClimbingRecordViewModel(repository)
            val effects = mutableListOf<TimerSubmissionEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.submissionEffect.collect(effects::add)
            }
            advanceUntilIdle()

            viewModel.submitRecord(3_723_000L)
            advanceUntilIdle()

            assertTrue(viewModel.submissionState.value is TimerSubmissionState.Preserved)
            assertTrue(TimerSubmissionEffect.NavigateToComplete !in effects)
        }

    @Test
    fun preservedDraft_confirmDiscard_clearsDraftAndEmitsDiscardOnce() =
        runTest(dispatcher) {
            coEvery { repository.getRecord(1) } returns savedRecord(time = "01:02:03")
            val viewModel = SetTimerClimbingRecordViewModel(repository)
            val effects = mutableListOf<TimerSubmissionEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.submissionEffect.collect(effects::add)
            }
            advanceUntilIdle()

            viewModel.discardPreservedDraft()
            advanceUntilIdle()

            assertEquals(TimerSubmissionState.Draft, viewModel.submissionState.value)
            assertEquals(listOf(TimerSubmissionEffect.DraftDiscarded), effects)
            coVerify(exactly = 1) { repository.clearTimerClimbingRecordDraft() }
        }

    @Test
    fun cancellation_afterSnapshot_rethrowsWithoutErrorEffect() =
        runTest(dispatcher) {
            coEvery { repository.finalizeTimerClimbingRecord(1, "01:02:03") } returns request
            coEvery { repository.createTimerClimbingRecord(request) } throws CancellationException("cancelled")
            val controller = TimerRecordSubmissionController(repository)
            val effects = mutableListOf<TimerSubmissionEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                controller.effect.collect(effects::add)
            }

            try {
                controller.submit(3_723_000L)
                fail("CancellationException should be rethrown")
            } catch (_: CancellationException) {
                // expected
            }

            assertTrue(controller.state.value is TimerSubmissionState.Preserved)
            assertTrue(effects.isEmpty())
        }

    @Test
    fun catalogFailure_retryCatalog_reissuesRequestWithoutRecreatingViewModel() =
        runTest(dispatcher) {
            coEvery { repository.getGymFilteringKey(1) } returnsMany listOf(
                BaseState.Error("server error", "500"),
                BaseState.Success(
                    GetGymFilteringKeyResponse(
                        difficultyList = emptyList(),
                        maxFloor = 1,
                        gymId = 1,
                        sectorList = emptyList(),
                        timePoint = "2026-09-21",
                    ),
                ),
            )
            coEvery { repository.getGymRouteInfoList(1, any()) } returns
                BaseState.Success(GetGymRouteInfoResponse(page = 0, hasNext = false, result = emptyList()))
            val viewModel = SetTimerClimbingRecordViewModel(repository)
            advanceUntilIdle()

            viewModel.selectedCrag(1, "테스트 암장")
            advanceUntilIdle()
            assertTrue(viewModel.isCatalogRetryAvailable.value)

            viewModel.retryCatalog()
            advanceUntilIdle()

            assertEquals(false, viewModel.isCatalogRetryAvailable.value)
            coVerify(exactly = 2) { repository.getGymFilteringKey(1) }
        }

    private fun savedRecord(time: String) =
        ClimbingRecordData(
            id = 1,
            gymId = 1,
            gymName = "테스트 암장",
            date = "2026-09-21",
            time = time,
        )
}
