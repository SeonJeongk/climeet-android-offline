package com.climus.climeet.data.repository.homegym

import com.climus.climeet.data.local.routecache.HomeGymCacheMetadataEntity
import com.climus.climeet.data.local.routecache.HomeGymEntity
import com.climus.climeet.data.local.routecache.RouteCacheDao
import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.model.ErrorType
import com.climus.climeet.data.model.response.UserHomeGymSimpleResponse
import com.climus.climeet.data.model.runRemote
import com.climus.climeet.data.remote.MainApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class HomeGymSnapshot(
    val gyms: List<UserHomeGymSimpleResponse>,
    val receivedAtEpochMs: Long?,
    val isRefreshing: Boolean,
    val refreshError: BaseState.Error?,
)

interface HomeGymRemoteDataSource {
    suspend fun fetch(): BaseState<List<UserHomeGymSimpleResponse>>
}

class RetrofitHomeGymRemoteDataSource @Inject constructor(
    private val api: MainApi,
) : HomeGymRemoteDataSource {
    override suspend fun fetch(): BaseState<List<UserHomeGymSimpleResponse>> = runRemote { api.getHomeGyms() }
}

interface HomeGymRepository {
    fun observe(accountId: Long): Flow<HomeGymSnapshot>

    suspend fun refresh(accountId: Long)

    suspend fun clear(accountId: Long)
}

@Singleton
class HomeGymRepositoryImpl @Inject constructor(
    private val dao: RouteCacheDao,
    private val remote: HomeGymRemoteDataSource,
    private val clock: Clock,
) : HomeGymRepository {
    private data class RefreshState(
        val refreshing: Boolean = false,
        val error: BaseState.Error? = null,
    )

    private val states = ConcurrentHashMap<Long, MutableStateFlow<RefreshState>>()

    override fun observe(accountId: Long): Flow<HomeGymSnapshot> =
        combine(
            dao.observeHomeGyms(accountId),
            dao.observeHomeGymMetadata(accountId),
            state(accountId),
        ) { gyms, metadata, refresh ->
            HomeGymSnapshot(
                gyms = gyms.map {
                    UserHomeGymSimpleResponse(it.gymId, it.gymProfileUrl, it.gymName, it.followerCount)
                },
                receivedAtEpochMs = metadata?.receivedAtEpochMs,
                isRefreshing = refresh.refreshing,
                refreshError = refresh.error,
            )
        }

    override suspend fun refresh(accountId: Long) {
        if (accountId <= 0L || state(accountId).value.refreshing) return
        state(accountId).value = RefreshState(refreshing = true)
        try {
            when (val result = remote.fetch()) {
                is BaseState.Success -> {
                    val receivedAt = clock.millis()
                    dao.replaceHomeGyms(
                        HomeGymCacheMetadataEntity(accountId, receivedAt),
                        result.body.mapIndexed { index, gym ->
                            HomeGymEntity(
                                accountId,
                                gym.gymId,
                                index,
                                gym.gymProfileUrl,
                                gym.gymName,
                                gym.followerCount,
                            )
                        },
                    )
                    state(accountId).value = RefreshState()
                }

                is BaseState.Error -> state(accountId).value = RefreshState(error = result)
            }
        } catch (error: CancellationException) {
            state(accountId).value = RefreshState()
            throw error
        } catch (error: Exception) {
            state(accountId).value = RefreshState(
                error = BaseState.Error(
                    "저장된 내 암장을 갱신하지 못했습니다",
                    "LOCAL_STORAGE",
                    error,
                    ErrorType.UNKNOWN,
                ),
            )
        }
    }

    override suspend fun clear(accountId: Long) {
        if (accountId <= 0L) return
        dao.clearHomeGyms(accountId)
        states.remove(accountId)?.value = RefreshState()
    }

    private fun state(accountId: Long) = states.computeIfAbsent(accountId) { MutableStateFlow(RefreshState()) }
}
