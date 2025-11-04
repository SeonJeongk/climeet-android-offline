package com.climus.climeet.data.repository.homegym

import com.climus.climeet.data.local.routecache.HomeGymCacheMetadataEntity
import com.climus.climeet.data.local.routecache.HomeGymEntity
import com.climus.climeet.data.local.routecache.RouteCacheDao
import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.model.ErrorType
import com.climus.climeet.data.model.response.UserHomeGymSimpleResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class HomeGymRepositoryTest {
    private val dao = mockk<RouteCacheDao>(relaxed = true)
    private val clock = Clock.fixed(Instant.ofEpochMilli(10_000L), ZoneOffset.UTC)

    @Test
    fun requestedAccount_refreshSuccess_replacesOnlyRequestedAccount() =
        runTest {
            val remote = FakeHomeGymRemote(
                BaseState.Success(
                    listOf(
                        UserHomeGymSimpleResponse(20, "b.png", "B", 2),
                        UserHomeGymSimpleResponse(10, "a.png", "A", 1),
                    ),
                ),
            )
            val repository = HomeGymRepositoryImpl(dao, remote, clock)

            repository.refresh(7L)

            coVerify(exactly = 1) {
                dao.replaceHomeGyms(
                    HomeGymCacheMetadataEntity(7L, 10_000L),
                    listOf(
                        HomeGymEntity(7L, 20, 0, "b.png", "B", 2),
                        HomeGymEntity(7L, 10, 1, "a.png", "A", 1),
                    ),
                )
            }
            coVerify(exactly = 0) { dao.clearHomeGyms(8L) }
        }

    @Test
    fun cachedAccount_refreshFailure_keepsListAndTimestamp() =
        runTest {
            val cached = listOf(HomeGymEntity(7L, 10, 0, "a.png", "A", 1))
            every { dao.observeHomeGyms(7L) } returns flowOf(cached)
            every { dao.observeHomeGymMetadata(7L) } returns flowOf(HomeGymCacheMetadataEntity(7L, 5_000L))
            val error = BaseState.Error("offline", "TRANSPORT", type = ErrorType.TRANSPORT)
            val repository = HomeGymRepositoryImpl(dao, FakeHomeGymRemote(error), clock)

            repository.refresh(7L)
            val snapshot = repository.observe(7L).first()

            assertEquals(listOf(10L), snapshot.gyms.map { it.gymId })
            assertEquals(5_000L, snapshot.receivedAtEpochMs)
            assertEquals(error, snapshot.refreshError)
            coVerify(exactly = 0) { dao.replaceHomeGyms(any(), any()) }
        }

    @Test
    fun cachedAccount_replaceFails_keepsListAndStopsRefreshing() =
        runTest {
            val cached = listOf(HomeGymEntity(7L, 10, 0, "a.png", "A", 1))
            every { dao.observeHomeGyms(7L) } returns flowOf(cached)
            every { dao.observeHomeGymMetadata(7L) } returns flowOf(HomeGymCacheMetadataEntity(7L, 5_000L))
            coEvery { dao.replaceHomeGyms(any(), any()) } throws IllegalStateException("database full")
            val repository = HomeGymRepositoryImpl(
                dao,
                FakeHomeGymRemote(BaseState.Success(listOf(UserHomeGymSimpleResponse(20, "b.png", "B", 2)))),
                clock,
            )

            repository.refresh(7L)
            val snapshot = repository.observe(7L).first()

            assertEquals(listOf(10L), snapshot.gyms.map { it.gymId })
            assertEquals(5_000L, snapshot.receivedAtEpochMs)
            assertFalse(snapshot.isRefreshing)
            assertEquals("LOCAL_STORAGE", snapshot.refreshError?.code)
        }

    @Test
    fun currentAccount_clear_removesOnlyCurrentAccount() =
        runTest {
            val repository = HomeGymRepositoryImpl(
                dao,
                FakeHomeGymRemote(BaseState.Success(emptyList())),
                clock,
            )

            repository.clear(7L)

            coVerify(exactly = 1) { dao.clearHomeGyms(7L) }
            coVerify(exactly = 0) { dao.clearHomeGyms(8L) }
        }
}

private class FakeHomeGymRemote(
    private val result: BaseState<List<UserHomeGymSimpleResponse>>,
) : HomeGymRemoteDataSource {
    override suspend fun fetch() = result
}
