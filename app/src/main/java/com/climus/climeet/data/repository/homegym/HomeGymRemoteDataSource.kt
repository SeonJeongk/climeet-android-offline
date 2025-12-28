package com.climus.climeet.data.repository.homegym

import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.model.response.UserHomeGymSimpleResponse
import com.climus.climeet.data.model.runRemote
import com.climus.climeet.data.remote.MainApi
import javax.inject.Inject

interface HomeGymRemoteDataSource {
    suspend fun fetch(): BaseState<List<UserHomeGymSimpleResponse>>
}

class RetrofitHomeGymRemoteDataSource @Inject constructor(
    private val api: MainApi,
) : HomeGymRemoteDataSource {
    override suspend fun fetch(): BaseState<List<UserHomeGymSimpleResponse>> = runRemote { api.getHomeGyms() }
}
