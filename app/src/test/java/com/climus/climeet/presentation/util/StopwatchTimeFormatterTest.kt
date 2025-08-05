package com.climus.climeet.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TimerTimeFormatterTest {
    @Test
    fun zeroDuration_formatTimerTime_distinguishesDisplayAndRequest() {
        assertEquals("00:00", formatTimerTime(0L))
        assertEquals("00:00:00", formatTimerTime(0L, alwaysShowHours = true))
    }

    @Test
    fun secondAndMinuteBoundaries_formatTimerTime_truncatesMilliseconds() {
        assertEquals("00:00", formatTimerTime(999L))
        assertEquals("00:01", formatTimerTime(1_000L))
        assertEquals("00:59", formatTimerTime(59_999L))
        assertEquals("01:00", formatTimerTime(60_000L))
        assertEquals("00:01:00", formatTimerTime(60_000L, alwaysShowHours = true))
    }

    @Test
    fun hourBoundary_formatTimerTime_addsHoursToDisplay() {
        assertEquals("59:59", formatTimerTime(3_599_999L))
        assertEquals("00:59:59", formatTimerTime(3_599_999L, alwaysShowHours = true))
        assertEquals("01:00:00", formatTimerTime(3_600_000L))
        assertEquals("01:00:00", formatTimerTime(3_600_000L, alwaysShowHours = true))
    }

    @Test
    fun durationBeyondDay_formatTimerTime_preservesTotalHours() {
        assertEquals("25:01:01", formatTimerTime(90_061_000L))
        assertEquals("100:00:00", formatTimerTime(360_000_000L, alwaysShowHours = true))
    }

    @Test
    fun arabicDefaultLocale_formatTimerTime_keepsAsciiDigits() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))

            assertEquals("01:02", formatTimerTime(62_000L))
            assertEquals("00:01:02", formatTimerTime(62_000L, alwaysShowHours = true))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun storedRequestDuration_parseTimerTime_restoresDisplayDuration() {
        val elapsedTime = parseTimerTime("00:01:19")

        assertEquals(79_000L, elapsedTime)
        assertEquals("01:19", formatTimerTime(requireNotNull(elapsedTime)))
    }

    @Test
    fun malformedDuration_parseTimerTime_returnsNull() {
        assertEquals(null, parseTimerTime(""))
        assertEquals(null, parseTimerTime("00:60"))
        assertEquals(null, parseTimerTime("00:01:60"))
        assertEquals(null, parseTimerTime("not-a-time"))
    }
}
