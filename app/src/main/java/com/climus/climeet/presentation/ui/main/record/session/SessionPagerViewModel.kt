package com.climus.climeet.presentation.ui.main.record.session

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SessionPagerViewModel @Inject constructor() : ViewModel() {
    private val _moveToStopwatchFragmentEvent = MutableLiveData<Unit>()
    val moveToStopwatchFragmentEvent: LiveData<Unit> get() = _moveToStopwatchFragmentEvent

    fun moveToStopwatch() {
        _moveToStopwatchFragmentEvent.value = Unit
    }
}
