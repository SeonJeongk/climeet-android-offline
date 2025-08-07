package com.climus.climeet.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class StopwatchTimeFormatterTest {
    @Test
    fun zeroDuration_formatStopwatchTime_distinguishesDisplayAndRequest() {
        assertEquals("00:00", formatStopwatchTime(0L))
        assertEquals("00:00:00", formatStopwatchTime(0L, alwaysShowHours = true))
    }

    @Test
    fun secondAndMinuteBoundaries_formatStopwatchTime_truncatesMilliseconds() {
        assertEquals("00:00", formatStopwatchTime(999L))
        assertEquals("00:01", formatStopwatchTime(1_000L))
        assertEquals("00:59", formatStopwatchTime(59_999L))
        assertEquals("01:00", formatStopwatchTime(60_000L))
        assertEquals("00:01:00", formatStopwatchTime(60_000L, alwaysShowHours = true))
    }

    @Test
    fun hourBoundary_formatStopwatchTime_addsHoursToDisplay() {
        assertEquals("59:59", formatStopwatchTime(3_599_999L))
        assertEquals("00:59:59", formatStopwatchTime(3_599_999L, alwaysShowHours = true))
        assertEquals("01:00:00", formatStopwatchTime(3_600_000L))
        assertEquals("01:00:00", formatStopwatchTime(3_600_000L, alwaysShowHours = true))
    }

    @Test
    fun durationBeyondDay_formatStopwatchTime_preservesTotalHours() {
        assertEquals("25:01:01", formatStopwatchTime(90_061_000L))
        assertEquals("100:00:00", formatStopwatchTime(360_000_000L, alwaysShowHours = true))
    }

    @Test
    fun arabicDefaultLocale_formatStopwatchTime_keepsAsciiDigits() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))

            assertEquals("01:02", formatStopwatchTime(62_000L))
            assertEquals("00:01:02", formatStopwatchTime(62_000L, alwaysShowHours = true))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun storedRequestDuration_parseStopwatchTime_restoresDisplayDuration() {
        val elapsedTime = parseStopwatchTime("00:01:19")

        assertEquals(79_000L, elapsedTime)
        assertEquals("01:19", formatStopwatchTime(requireNotNull(elapsedTime)))
    }

    @Test
    fun malformedDuration_parseStopwatchTime_returnsNull() {
        assertEquals(null, parseStopwatchTime(""))
        assertEquals(null, parseStopwatchTime("00:60"))
        assertEquals(null, parseStopwatchTime("00:01:60"))
        assertEquals(null, parseStopwatchTime("not-a-time"))
    }
}
