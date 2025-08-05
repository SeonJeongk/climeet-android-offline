package com.climus.climeet.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.climus.climeet.R
import com.climus.climeet.presentation.ui.main.MainActivity
import com.climus.climeet.presentation.util.formatStopwatchTime
import dagger.hilt.android.AndroidEntryPoint
import java.util.Timer
import java.util.TimerTask

@AndroidEntryPoint
class StopwatchService : Service() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private var startTime = 0L
    private var pauseTime = 0L
    private var elapsedTime = 0L
    private var isStart = false
    private var isPaused = false
    private var isRestart = false
    private var isStopped = true
    private var isRunning = false
    private var timer: Timer? = null

    override fun onBind(intent: Intent): IBinder = throw UnsupportedOperationException("Not yet implemented")

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val command = intent?.getStringExtra("command")

        when (command) {
            "START" -> {
                startStopwatch(intent.getLongExtra(EXTRA_INITIAL_ELAPSED_TIME, 0L))
                val notification = createNotification(elapsedTime)
                startForeground(1, notification)
            }

            "PAUSE" -> pauseStopwatch()
            "RESTART" -> restartStopwatch()
        }

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannelAndGroup()

        sharedPreferences = applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        editor = sharedPreferences.edit()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStopwatch()
    }

    private fun createNotificationChannelAndGroup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val groupName = "운동 설정"
            val channelGroup = NotificationChannelGroup(GROUP_ID, groupName)
            notificationManager.createNotificationChannelGroup(channelGroup)

            val channelName = "운동 시간"
            val descriptionText = "운동한 시간을 알려줍니다."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, channelName, importance).apply {
                description = descriptionText
                group = GROUP_ID
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(elapsedTime: Long): Notification {
        val timeFormat = formatStopwatchTime(elapsedTime)

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_SHOW_CLIMBING_SESSION, true)
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(timeFormat)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            notification.setSound(null)
        }

        if (isPaused) {
            val restartIntent = Intent(this, StopwatchService::class.java).apply {
                putExtra("command", "RESTART")
            }
            val restartPendingIntent: PendingIntent =
                PendingIntent.getService(this, 0, restartIntent, PendingIntent.FLAG_MUTABLE)
            notification.addAction(R.drawable.ic_notification, "재시작", restartPendingIntent)
        } else {
            val pauseIntent = Intent(this, StopwatchService::class.java).apply {
                putExtra("command", "PAUSE")
            }
            val pausePendingIntent: PendingIntent =
                PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_MUTABLE)
            notification.addAction(R.drawable.ic_notification, "일시중지", pausePendingIntent)
        }

        return notification.build()
    }

    private fun scheduleTicks() {
        if (!isPaused) {
            startTime = System.currentTimeMillis()
        }
        timer = Timer().apply {
            schedule(
                object : TimerTask() {
                    override fun run() {
                        elapsedTime += System.currentTimeMillis() - startTime
                        editor.putLong(PAUSE_TIME, elapsedTime).apply()
                        val notification = createNotification(elapsedTime)
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(1, notification)

                        val intent = Intent("StopwatchUpdate")
                        intent.putExtra("elapsedTime", elapsedTime)
                        LocalBroadcastManager.getInstance(this@StopwatchService).sendBroadcast(intent)

                        startTime = System.currentTimeMillis()
                    }
                },
                0,
                1000,
            )
        }
    }

    private fun startStopwatch(initialElapsedTime: Long) {
        elapsedTime = initialElapsedTime.coerceAtLeast(0L)
        pauseTime = elapsedTime
        scheduleTicks()
        isStart = true
        isPaused = false
        isRestart = false
        isStopped = false
        isRunning = true

        editor.putBoolean(KEY_IS_START, isStart)
        editor.putBoolean(KEY_IS_PAUSE, isPaused)
        editor.putBoolean(KEY_IS_RESTART, isRestart)
        editor.putBoolean(KEY_IS_STOP, isStopped)
        editor.putBoolean(KEY_IS_RUNNING, isRunning)
        editor.putLong(PAUSE_TIME, elapsedTime)
        editor.apply()

        hasStartedInProcess.postValue(true)
    }

    private fun pauseStopwatch() {
        isStart = false
        isPaused = true
        isRestart = false
        isStopped = false
        isRunning = false
        timer?.cancel()
        pauseTime = elapsedTime

        // 일시정지 시각을 반영하려면 알림을 다시 게시해야 합니다.
        val notification = createNotification(elapsedTime)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)

        val intent = Intent("StopwatchUpdate")
        intent.putExtra("elapsedTime", elapsedTime)
        intent.putExtra("pauseTime", pauseTime)
        intent.putExtra("pauseState", "yes")
        LocalBroadcastManager.getInstance(this@StopwatchService).sendBroadcast(intent)

        editor.putLong("pauseTime", pauseTime)
        editor.putBoolean(KEY_IS_START, isStart)
        editor.putBoolean(KEY_IS_PAUSE, isPaused)
        editor.putBoolean(KEY_IS_RESTART, isRestart)
        editor.putBoolean(KEY_IS_STOP, isStopped)
        editor.putBoolean(KEY_IS_RUNNING, isRunning)
        editor.apply()
    }

    private fun restartStopwatch() {
        isStart = false
        isPaused = false
        isRestart = true
        isStopped = false
        isRunning = true
        scheduleTicks()

        val intent = Intent("StopwatchUpdate")
        intent.putExtra("pauseState", "no")
        LocalBroadcastManager.getInstance(this@StopwatchService).sendBroadcast(intent)

        editor.putString("pauseTimeFormat", "00:00")
        editor.putBoolean(KEY_IS_START, isStart)
        editor.putBoolean(KEY_IS_PAUSE, isPaused)
        editor.putBoolean(KEY_IS_RESTART, isRestart)
        editor.putBoolean(KEY_IS_STOP, isStopped)
        editor.putBoolean(KEY_IS_RUNNING, isRunning)
        editor.apply()
    }

    private fun stopStopwatch() {
        isStart = false
        isPaused = false
        isRestart = false
        isStopped = true
        isRunning = false
        timer?.cancel()

        val intent = Intent("StopwatchUpdate")
        intent.putExtra("elapsedTime", 0L)
        LocalBroadcastManager.getInstance(this@StopwatchService).sendBroadcast(intent)

        editor.putBoolean(KEY_IS_START, isStart)
        editor.putBoolean(KEY_IS_PAUSE, isPaused)
        editor.putBoolean(KEY_IS_RESTART, isRestart)
        editor.putBoolean(KEY_IS_STOP, isStopped)
        editor.putBoolean(KEY_IS_RUNNING, isRunning)
        editor.apply()
    }

    companion object {
        const val PREF_NAME = "timer_prefs"
        private const val CHANNEL_ID = "stopwatch_channel"
        private const val GROUP_ID = "exercise_record_group"

        /**
         * 앱이 실행된 뒤 스톱워치 서비스가 시작됐는지 나타냅니다.
         *
         * 앱이 완전히 종료됐다가 다시 실행되면 `null`로 돌아갑니다.
         * 이때 화면은 기기에 저장된 운동 기록을 복구합니다.
         */
        val hasStartedInProcess = MutableLiveData<Boolean>()
        const val PAUSE_TIME = "pauseTime"
        const val KEY_IS_START = "isStart"
        const val KEY_IS_PAUSE = "isPause"
        const val KEY_IS_RESTART = "isRestart"
        const val KEY_IS_STOP = "isStop"
        const val KEY_IS_RUNNING = "isRunning"
        const val EXTRA_INITIAL_ELAPSED_TIME = "initialElapsedTime"
    }
}
