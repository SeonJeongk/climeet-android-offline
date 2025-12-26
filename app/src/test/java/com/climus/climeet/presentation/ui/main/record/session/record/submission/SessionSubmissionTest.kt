package com.climus.climeet.presentation.ui.main.record.session.record.submission

import android.content.SharedPreferences
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.climus.climeet.app.App
import com.climus.climeet.data.local.ClimbingRecordData
import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.model.request.CreateClimbingRecordRequest
import com.climus.climeet.data.repository.MainRepository
import com.climus.climeet.data.repository.route.Freshness
import com.climus.climeet.data.repository.route.RouteCatalog
import com.climus.climeet.data.repository.route.RouteCatalogKey
import com.climus.climeet.data.repository.route.RouteCatalogRepository
import com.climus.climeet.data.repository.route.RouteCatalogState
import com.climus.climeet.presentation.ui.main.record.session.record.SessionRecordViewModel
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SessionSubmissionTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<MainRepository>(relaxed = true)
    private val request = CreateClimbingRecordRequest(1, "2026-09-21", "01:02:03", 5, emptyList())

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
            coEvery { repository.finalizeSessionRecord(1, "01:02:03") } returns request
            coEvery { repository.createClimbingRecord(request) } returns BaseState.Success("".toResponseBody())
            val viewModel = SessionRecordViewModel(repository, LoadingRouteCatalogRepository)
            val effects = mutableListOf<SessionSubmissionEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.submissionEffect.collect(effects::add)
            }
            advanceUntilIdle()

            viewModel.submitRecord(3_723_000L)
            advanceUntilIdle()

            assertEquals(SessionSubmissionState.Succeeded, viewModel.submissionState.value)
            assertEquals(listOf(SessionSubmissionEffect.NavigateToComplete), effects)
            coVerify(exactly = 1) { repository.clearSessionDraft() }

            viewModel.acknowledgeSubmissionSuccess()
            advanceUntilIdle()
            assertEquals(SessionSubmissionState.Draft, viewModel.submissionState.value)
        }

    @Test
    fun failedPost_submit_preservesDraftAndBlocksSecondPost() =
        runTest(dispatcher) {
            coEvery { repository.finalizeSessionRecord(1, "01:02:03") } returns request
            coEvery { repository.createClimbingRecord(request) } returns BaseState.Error("server error", "500")
            val viewModel = SessionRecordViewModel(repository, LoadingRouteCatalogRepository)
            advanceUntilIdle()

            viewModel.submitRecord(3_723_000L)
            advanceUntilIdle()
            viewModel.submitRecord(3_723_000L)
            advanceUntilIdle()

            assertTrue(viewModel.submissionState.value is SessionSubmissionState.Preserved)
            coVerify(exactly = 1) { repository.createClimbingRecord(request) }
            coVerify(exactly = 0) { repository.clearSessionDraft() }
        }

    @Test
    fun duplicateInput_whileSubmitting_sendsSinglePost() =
        runTest(dispatcher) {
            coEvery { repository.finalizeSessionRecord(1, "01:02:03") } returns request
            coEvery { repository.createClimbingRecord(request) } coAnswers {
                delay(800)
                BaseState.Success("".toResponseBody())
            }
            val viewModel = SessionRecordViewModel(repository, LoadingRouteCatalogRepository)
            advanceUntilIdle()

            repeat(10) { viewModel.submitRecord(3_723_000L) }
            runCurrent()
            assertEquals(SessionSubmissionState.Submitting, viewModel.submissionState.value)
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.createClimbingRecord(request) }
        }

    @Test
    fun pendingDraftMutation_submit_waitsForLastWriteBeforeFinalizingSnapshot() =
        runTest(dispatcher) {
            val mutationStarted = CompletableDeferred<Unit>()
            val releaseMutation = CompletableDeferred<Unit>()
            val order = mutableListOf<String>()
            coEvery { repository.finalizeSessionRecord(1, "01:02:03") } coAnswers {
                order += "finalize"
                request
            }
            coEvery { repository.createClimbingRecord(request) } returns BaseState.Success("".toResponseBody())
            val controller = SessionSubmissionController(repository)

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

            coVerify(exactly = 0) { repository.finalizeSessionRecord(any(), any()) }
            releaseMutation.complete(Unit)
            mutation.join()
            submission.join()

            assertEquals(listOf("mutation-start", "mutation-end", "finalize"), order)
            coVerify(exactly = 1) { repository.createClimbingRecord(request) }
        }

    @Test
    fun failedPost_recreatedViewModel_restoresPreservedDraftWithoutResending() =
        runTest(dispatcher) {
            coEvery { repository.finalizeSessionRecord(1, "01:02:03") } returns request
            coEvery { repository.createClimbingRecord(request) } returns BaseState.Error("server error", "500")
            val firstViewModel = SessionRecordViewModel(repository, LoadingRouteCatalogRepository)
            advanceUntilIdle()

            firstViewModel.submitRecord(3_723_000L)
            advanceUntilIdle()
            coEvery { repository.getRecord(1) } returns savedRecord(time = "01:02:03")

            val recreatedViewModel = SessionRecordViewModel(repository, LoadingRouteCatalogRepository)
            advanceUntilIdle()
            recreatedViewModel.submitRecord(3_723_000L)
            advanceUntilIdle()

            assertTrue(recreatedViewModel.submissionState.value is SessionSubmissionState.Preserved)
            coVerify(exactly = 1) { repository.createClimbingRecord(request) }
        }

    @Test
    fun finalizedDraft_restore_formatsDurationForStopwatchDisplay() =
        runTest(dispatcher) {
            coEvery { repository.getRecord(1) } returns savedRecord(time = "00:01:19")

            val viewModel = SessionRecordViewModel(repository, LoadingRouteCatalogRepository)
            advanceUntilIdle()

            assertEquals(79_000L, viewModel.restoredElapsedTimeMillis.value)
            assertEquals("01:19", viewModel.restoredDuration.value)
        }

    @Test
    fun pausedDraft_restore_usesPersistedElapsedTime() =
        runTest(dispatcher) {
            coEvery { repository.getRecord(1) } returns savedRecord(time = "")
            every { App.sharedPreferences.getLong("pauseTime", 0L) } returns 79_000L

            val viewModel = SessionRecordViewModel(repository, LoadingRouteCatalogRepository)
            advanceUntilIdle()

            assertEquals(SessionSubmissionState.Draft, viewModel.submissionState.value)
            assertEquals(79_000L, viewModel.restoredElapsedTimeMillis.value)
            assertEquals("01:19", viewModel.restoredDuration.value)
        }

    @Test
    fun localSnapshotFailure_submit_returnsToDraftWithoutPosting() =
        runTest(dispatcher) {
            coEvery { repository.finalizeSessionRecord(1, "01:02:03") } throws IllegalStateException("disk")
            val viewModel = SessionRecordViewModel(repository, LoadingRouteCatalogRepository)
            advanceUntilIdle()

            viewModel.submitRecord(3_723_000L)
            advanceUntilIdle()

            assertEquals(SessionSubmissionState.Draft, viewModel.submissionState.value)
            coVerify(exactly = 0) { repository.createClimbingRecord(any()) }
            coVerify(exactly = 0) { repository.clearSessionDraft() }
        }

    @Test
    fun localClearFailure_afterSuccessfulPost_preservesDraftWithoutNavigation() =
        runTest(dispatcher) {
            coEvery { repository.finalizeSessionRecord(1, "01:02:03") } returns request
            coEvery { repository.createClimbingRecord(request) } returns BaseState.Success("".toResponseBody())
            coEvery { repository.clearSessionDraft() } throws IllegalStateException("disk")
            val viewModel = SessionRecordViewModel(repository, LoadingRouteCatalogRepository)
            val effects = mutableListOf<SessionSubmissionEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.submissionEffect.collect(effects::add)
            }
            advanceUntilIdle()

            viewModel.submitRecord(3_723_000L)
            advanceUntilIdle()

            assertTrue(viewModel.submissionState.value is SessionSubmissionState.Preserved)
            assertTrue(SessionSubmissionEffect.NavigateToComplete !in effects)
        }

    @Test
    fun preservedDraft_confirmDiscard_clearsDraftAndEmitsDiscardOnce() =
        runTest(dispatcher) {
            coEvery { repository.getRecord(1) } returns savedRecord(time = "01:02:03")
            val viewModel = SessionRecordViewModel(repository, LoadingRouteCatalogRepository)
            val effects = mutableListOf<SessionSubmissionEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.submissionEffect.collect(effects::add)
            }
            advanceUntilIdle()

            viewModel.discardPreservedDraft()
            advanceUntilIdle()

            assertEquals(SessionSubmissionState.Draft, viewModel.submissionState.value)
            assertEquals(listOf(SessionSubmissionEffect.DraftDiscarded), effects)
            coVerify(exactly = 1) { repository.clearSessionDraft() }
        }

    @Test
    fun cancellation_afterSnapshot_rethrowsWithoutErrorEffect() =
        runTest(dispatcher) {
            coEvery { repository.finalizeSessionRecord(1, "01:02:03") } returns request
            coEvery { repository.createClimbingRecord(request) } throws CancellationException("cancelled")
            val controller = SessionSubmissionController(repository)
            val effects = mutableListOf<SessionSubmissionEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                controller.effect.collect(effects::add)
            }

            try {
                controller.submit(3_723_000L)
                fail("CancellationException should be rethrown")
            } catch (_: CancellationException) {
                // expected
            }

            assertTrue(controller.state.value is SessionSubmissionState.Preserved)
            assertTrue(effects.isEmpty())
        }

    @Test
    fun catalogFailure_retryCatalog_reissuesRequestWithoutRecreatingViewModel() =
        runTest(dispatcher) {
            val routeRepository = RetryRouteCatalogRepository()
            val viewModel = SessionRecordViewModel(repository, routeRepository)
            advanceUntilIdle()

            viewModel.selectGym(1, "테스트 암장")
            advanceUntilIdle()
            assertTrue(viewModel.isCatalogRetryAvailable.value)

            viewModel.retryCatalog()
            advanceUntilIdle()

            assertEquals(false, viewModel.isCatalogRetryAvailable.value)
            assertEquals(2, routeRepository.observeCount)
        }

    @Test
    fun restoredDraft_catalogKey_usesDraftDateUntilStop() =
        runTest(dispatcher) {
            coEvery { repository.getRecord(1) } returns savedRecord("00:10")
            val routeRepository = RetryRouteCatalogRepository(alwaysFail = true)
            val viewModel = SessionRecordViewModel(repository, routeRepository)
            viewModel.restoreDraft()
            advanceUntilIdle()

            assertEquals(LocalDate.parse("2026-09-21"), routeRepository.observedKeys.last().queryDate)

            viewModel.resetAtStop()
            viewModel.selectGym(1, "테스트 암장")
            advanceUntilIdle()

            assertEquals(LocalDate.now(), routeRepository.observedKeys.last().queryDate)
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

private object LoadingRouteCatalogRepository : RouteCatalogRepository {
    override fun observe(key: RouteCatalogKey): Flow<RouteCatalogState> = flowOf(RouteCatalogState.Loading)
}

private class RetryRouteCatalogRepository(
    private val alwaysFail: Boolean = false,
) : RouteCatalogRepository {
    var observeCount = 0
    val observedKeys = mutableListOf<RouteCatalogKey>()

    override fun observe(key: RouteCatalogKey): Flow<RouteCatalogState> =
        flow {
            observeCount++
            observedKeys += key
            if (alwaysFail || observeCount == 1) {
                emit(RouteCatalogState.Failed(BaseState.Error("server error", "500")))
            } else {
                emit(
                    RouteCatalogState.Content(
                        RouteCatalog(1, null, key.queryDate.toString(), emptyList(), emptyList(), emptyList()),
                        Freshness.FRESH,
                        false,
                        1L,
                    ),
                )
            }
        }
}
