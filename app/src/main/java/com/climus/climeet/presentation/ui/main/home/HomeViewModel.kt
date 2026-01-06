package com.climus.climeet.presentation.ui.main.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.climus.climeet.app.App.Companion.sharedPreferences
import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.model.response.BannerDetailInfoResponse
import com.climus.climeet.data.model.response.BestFollowGymSimpleResponse
import com.climus.climeet.data.model.response.BestRecordGymDetailInfoResponse
import com.climus.climeet.data.model.response.BestRouteDetailInfoResponse
import com.climus.climeet.data.model.response.UserHomeGymSimpleResponse
import com.climus.climeet.data.model.response.UserProfileInfoResponse
import com.climus.climeet.data.repository.MainRepository
import com.climus.climeet.data.repository.homegym.HomeGymRepository
import com.climus.climeet.presentation.ui.main.global.selectsector.model.SelectedFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val bannerList: List<BannerDetailInfoResponse> = emptyList(),
    val homegymList: List<UserHomeGymSimpleResponse> = emptyList(),
    val followOrderCragList: List<BestFollowGymSimpleResponse> = emptyList(),
    val recordOrderCragList: List<BestRecordGymDetailInfoResponse> = emptyList(),
    val routeList: List<BestRouteDetailInfoResponse> = emptyList(),
    val curFilter: SelectedFilter = SelectedFilter(),
    val page: Int = 0,
    val hasNext: Boolean = true,
    val myProfile: UserProfileInfoResponse? = null,
)

sealed class HomeEvent {
    data class NavigateToShortsPlayer(
        val shortsId: Long,
        val position: Int,
    ) : HomeEvent()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MainRepository,
    private val homeGymRepository: HomeGymRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<HomeEvent>()
    val event: SharedFlow<HomeEvent> = _event.asSharedFlow()

    private var accountId = sharedPreferences.getString(USER_ID_KEY, null)?.toLongOrNull() ?: 0L
    private var observedAccountId = 0L
    private var homeGymJob: Job? = null

    init {
        if (accountId > 0L) observeHomeGyms(accountId)
    }

    fun getBannerListBetweenDates() {
        viewModelScope.launch {
            repository.findBannerListBetweenDates().let {
                when (it) {
                    is BaseState.Success -> {
                        _uiState.update { state ->
                            state.copy(bannerList = it.body)
                        }
                    }

                    is BaseState.Error -> {
                        it.msg // 서버 에러 메시지
                        Log.d("Banner List API", it.msg)
                    }
                }
            }
        }
    }

    fun getHomeGyms() {
        if (accountId <= 0L) return
        observeHomeGyms(accountId)
        viewModelScope.launch { homeGymRepository.refresh(accountId) }
    }

    fun getGymRankingOrderFollowCount() {
        viewModelScope.launch {
            repository.findGymRankingOrderFollowCount().let {
                when (it) {
                    is BaseState.Success -> {
                        _uiState.update { state ->
                            state.copy(followOrderCragList = it.body)
                        }
                    }

                    is BaseState.Error -> {
                        it.msg // 서버 에러 메시지
                        Log.d("API", it.msg)
                    }
                }
            }
        }
    }

    fun getGymRankingListOrderSelectionCount() {
        viewModelScope.launch {
            repository.findGymRankingListOrderSelectionCount().let {
                when (it) {
                    is BaseState.Success -> {
                        _uiState.update { state ->
                            state.copy(recordOrderCragList = it.body)
                        }
                    }

                    is BaseState.Error -> {
                        it.msg // 서버 에러 메시지
                        Log.d("API", it.msg)
                    }
                }
            }
        }
    }

    fun getRouteRankingOrderSelectionCount() {
        viewModelScope.launch {
            repository.findRouteRankingOrderSelectionCount().let {
                when (it) {
                    is BaseState.Success -> {
                        _uiState.update { state ->
                            state.copy(routeList = it.body)
                        }
                    }

                    is BaseState.Error -> {
                        it.msg // 서버 에러 메시지
                        Log.d("API", it.msg)
                    }
                }
            }
        }
    }

    fun getUserProfile() {
        viewModelScope.launch {
            repository.getUserProfile().let {
                when (it) {
                    is BaseState.Success -> {
                        switchHomeGymAccount(it.body.userId)
                        _uiState.update { state ->
                            state.copy(myProfile = it.body)
                        }
                    }

                    is BaseState.Error -> {
                        it.msg // 서버 에러 메시지
                        Log.d("API", it.msg)
                    }
                }
            }
        }
    }

    fun clearHomeGymsForLogout() {
        val previous = accountId
        accountId = 0L
        observedAccountId = 0L
        homeGymJob?.cancel()
        _uiState.update { it.copy(homegymList = emptyList()) }
        viewModelScope.launch { homeGymRepository.clear(previous) }
    }

    private fun switchHomeGymAccount(nextAccountId: Long) {
        if (nextAccountId <= 0L || nextAccountId == accountId) {
            if (nextAccountId > 0L) {
                observeHomeGyms(nextAccountId)
                viewModelScope.launch { homeGymRepository.refresh(nextAccountId) }
            }
            return
        }
        val previous = accountId
        accountId = nextAccountId
        observeHomeGyms(nextAccountId)
        viewModelScope.launch {
            homeGymRepository.clear(previous)
            homeGymRepository.refresh(nextAccountId)
        }
    }

    private fun observeHomeGyms(id: Long) {
        if (id <= 0L || homeGymJob?.isActive == true && id == observedAccountId) return
        homeGymJob?.cancel()
        observedAccountId = id
        homeGymJob = viewModelScope.launch {
            homeGymRepository.observe(id).collect { snapshot ->
                _uiState.update { it.copy(homegymList = snapshot.gyms) }
            }
        }
    }

    private companion object {
        const val USER_ID_KEY = "USER_ID"
    }
}
