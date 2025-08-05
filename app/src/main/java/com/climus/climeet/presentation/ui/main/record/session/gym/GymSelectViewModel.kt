package com.climus.climeet.presentation.ui.main.record.session.gym

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.repository.MainRepository
import com.climus.climeet.presentation.ui.main.global.searchcrag.SearchCragUiData
import com.climus.climeet.presentation.ui.main.shorts.toSearchCragUiData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GymSearchUiState(
    var searchList: List<SearchCragUiData> = emptyList(),
    val progressState: Boolean = false,
    val emptyResultState: Boolean = false,
    val emptyTextState: Boolean = false,
)

sealed class GymSelectEvent {
    data class NavigateToBack(
        val id: Long,
        val name: String,
    ) : GymSelectEvent()

    data class ShowToastMessage(
        val msg: String,
    ) : GymSelectEvent()
}

@HiltViewModel
class GymSelectViewModel @Inject constructor(
    private val repository: MainRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GymSearchUiState())
    val uiState: StateFlow<GymSearchUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<GymSelectEvent>()
    val event: SharedFlow<GymSelectEvent> = _event.asSharedFlow()

    private var curJob: Job? = null

    val keyword = MutableStateFlow("")

    private val _selectedGym = MutableLiveData<SearchCragUiData?>()
    val selectedGym: MutableLiveData<SearchCragUiData?> get() = _selectedGym

    fun selectItem(item: SearchCragUiData) {
        _selectedGym.value = item
    }

    fun resetItem() {
        _selectedGym.value = null
    }

    init {
        observeKeyword()
    }

    private fun observeKeyword() {
        keyword.onEach {
            if (it.isBlank()) {
                _uiState.update { state ->
                    state.copy(
                        searchList = emptyList(),
                        emptyResultState = false,
                        emptyTextState = false,
                    )
                }
            } else {
                curJob?.cancel()

                _uiState.update { state ->
                    state.copy(
                        progressState = true,
                        emptyResultState = false,
                        emptyTextState = false,
                    )
                }
                curJob = viewModelScope.launch {
                    delay(500)
                    repository.searchAvailableGym(it, 0, 15).let { result ->
                        when (result) {
                            is BaseState.Success -> {
                                if (result.body.result.isNotEmpty()) {
                                    _uiState.update { state ->
                                        state.copy(
                                            searchList = result.body.result.map { item ->
                                                item.toSearchCragUiData(
                                                    it,
                                                    ::navigateToBack,
                                                )
                                            },
                                            progressState = false,
                                        )
                                    }
                                } else {
                                    _uiState.update { state ->
                                        state.copy(
                                            searchList = emptyList(),
                                            progressState = false,
                                            emptyResultState = true,
                                            emptyTextState = false,
                                        )
                                    }
                                }
                            }

                            is BaseState.Error -> {
                                _uiState.update { state ->
                                    state.copy(
                                        progressState = false,
                                        emptyResultState = true,
                                        emptyTextState = false,
                                    )
                                }
                                _event.emit(GymSelectEvent.ShowToastMessage(result.msg))
                            }
                        }
                    }
                }
            }
        }.launchIn(viewModelScope)
    }

    fun deleteKeyword() {
        keyword.value = ""
        _uiState.update { state ->
            state.copy(
                searchList = emptyList(),
                progressState = false,
                emptyResultState = true,
                emptyTextState = true,
            )
        }
    }

    fun navigateToBack(
        gymId: Long,
        gymName: String,
    ) {
        viewModelScope.launch {
            _event.emit(GymSelectEvent.NavigateToBack(gymId, gymName))
        }
    }
}
