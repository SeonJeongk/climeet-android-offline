package com.climus.climeet.data.repository.homegym

import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.model.response.UserHomeGymSimpleResponse
import kotlinx.coroutines.flow.Flow

data class HomeGymSnapshot(
    val gyms: List<UserHomeGymSimpleResponse>,
    val receivedAtEpochMs: Long?,
    val isRefreshing: Boolean,
    val refreshError: BaseState.Error?,
)

interface HomeGymRepository {
    fun observe(accountId: Long): Flow<HomeGymSnapshot>

    suspend fun refresh(accountId: Long)

    suspend fun clear(accountId: Long)
}
