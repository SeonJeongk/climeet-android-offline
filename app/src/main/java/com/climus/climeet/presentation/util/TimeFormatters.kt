package com.climus.climeet.presentation.util

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

private val recordTimeFormatter = DateTimeFormatter.ofPattern("a hh:mm", Locale.ENGLISH)

/**
 * 경과 시간을 화면·알림·서버 전송에서 사용하는 문자열로 변환합니다.
 *
 * 한 시간 미만은 `MM:ss`로 표시합니다. 시간이 있거나 [alwaysShowHours]가 `true`이면 `HH:mm:ss`를 사용합니다.
 */
internal fun formatStopwatchTime(
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

/**
 * `MM:ss` 또는 `HH:mm:ss` 형식의 경과 시간을 millisecond로 변환합니다.
 *
 * 분과 초가 0부터 59 범위를 벗어나거나 계산 결과가 넘치면 `null`을 반환합니다.
 */
internal fun parseStopwatchTime(stopwatchTime: String): Long? {
    val parts = stopwatchTime.split(':')
    if (parts.size !in 2..3) return null

    val values = parts.map { part -> part.toLongOrNull() ?: return null }
    val hours = if (values.size == 3) values[0] else 0L
    val minutes = values[values.size - 2]
    val seconds = values.last()
    if (hours < 0 || minutes !in 0..59 || seconds !in 0..59) return null

    return try {
        Math.addExact(
            Math.addExact(
                Math.multiplyExact(hours, TimeUnit.HOURS.toMillis(1)),
                Math.multiplyExact(minutes, TimeUnit.MINUTES.toMillis(1)),
            ),
            Math.multiplyExact(seconds, TimeUnit.SECONDS.toMillis(1)),
        )
    } catch (_: ArithmeticException) {
        null
    }
}

internal fun formatRecordTimeRange(
    start: LocalTime,
    end: LocalTime,
): String = "${start.format(recordTimeFormatter)} - ${end.format(recordTimeFormatter)}"
