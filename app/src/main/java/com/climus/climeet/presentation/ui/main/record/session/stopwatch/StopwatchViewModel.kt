package com.climus.climeet.presentation.ui.main.record.session.stopwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.climus.climeet.app.App.Companion.sharedPreferences
import com.climus.climeet.presentation.util.formatStopwatchTime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class StopwatchViewModel @Inject constructor() : ViewModel() {
    private val _time = MutableLiveData<Long>().apply { value = 0L }
    val time: LiveData<Long> get() = _time
    var pauseState = MutableLiveData<String>()

    var pauseTime = MutableLiveData<Long>()
    var isRunning = MutableLiveData<Boolean>().apply { value = false }
    var isStart = MutableLiveData<Boolean>().apply { value = false }
    var isPaused = MutableLiveData<Boolean>().apply { value = false }
    var isRestart = MutableLiveData<Boolean>().apply { value = false }
    var isStop = MutableLiveData<Boolean>().apply { value = true }

    var challengeNum = MutableLiveData<Long>()

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent,
        ) {
            _time.value = intent.getLongExtra("elapsedTime", 0L)
            val state = intent.getStringExtra("pauseState")
            if (state != null) {
                pauseState.value = state.toString()
                pauseTime.value = intent.getLongExtra("pauseTime", sharedPreferences.getLong("pauseTime", 0L))
            }
        }
    }

    val timeFormat: LiveData<String> = time.map { elapsedTime ->
        formatStopwatchTime(elapsedTime)
    }

    val pauseTimeFormat: LiveData<String> = pauseTime.map { pauseTime ->
        formatStopwatchTime(pauseTime)
    }

    fun restoreElapsedTime(elapsedTimeMillis: Long) {
        _time.value = elapsedTimeMillis
        pauseTime.value = elapsedTimeMillis
    }

    fun registerReceiver(context: Context) {
        LocalBroadcastManager.getInstance(context).registerReceiver(
            broadcastReceiver,
            IntentFilter(STOPWATCH_UPDATE),
        )
    }

    fun unregisterReceiver(context: Context) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(broadcastReceiver)
    }

    companion object {
        const val STOPWATCH_UPDATE = "StopwatchUpdate"
    }
}
