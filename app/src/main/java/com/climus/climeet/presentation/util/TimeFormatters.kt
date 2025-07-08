package com.climus.climeet.presentation.util

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

private val recordTimeFormatter = DateTimeFormatter.ofPattern("a hh:mm", Locale.ENGLISH)

// 화면·알림·서버 전송에서 같은 경과시간 계산과 숫자 형식을 사용합니다.
internal fun formatTimerTime(
    elapsedTimeMillis: Long,
    alwaysShowHours: Boolean = false,
): String {
    val hours = TimeUnit.MILLISECONDS.toHours(elapsedTimeMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTimeMillis) % TimeUnit.HOURS.toMinutes(1)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTimeMillis) % TimeUnit.MINUTES.toSeconds(1)

    return if (alwaysShowHours || hours > 0) {
        String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

internal fun formatRecordTimeRange(
    start: LocalTime,
    end: LocalTime,
): String = "${start.format(recordTimeFormatter)} - ${end.format(recordTimeFormatter)}"
