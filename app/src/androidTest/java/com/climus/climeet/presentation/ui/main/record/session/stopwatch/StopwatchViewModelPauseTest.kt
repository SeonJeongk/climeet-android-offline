package com.climus.climeet.presentation.ui.main.record.session.stopwatch

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StopwatchViewModelPauseTest {
    @Test
    fun pauseBroadcast_updatesElapsedAndPauseTimeFromSameSnapshot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var elapsedTime: Long? = null
        var pauseTime: Long? = null

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val viewModel = StopwatchViewModel()
            viewModel.registerReceiver(context)
            try {
                LocalBroadcastManager.getInstance(context).sendBroadcastSync(
                    Intent(StopwatchViewModel.STOPWATCH_UPDATE).apply {
                        putExtra("elapsedTime", 123_456L)
                        putExtra("pauseTime", 123_456L)
                        putExtra("pauseState", "yes")
                    },
                )

                elapsedTime = viewModel.time.value
                pauseTime = viewModel.pauseTime.value
            } finally {
                viewModel.unregisterReceiver(context)
            }
        }

        assertEquals(123_456L, elapsedTime)
        assertEquals(123_456L, pauseTime)
    }
}
