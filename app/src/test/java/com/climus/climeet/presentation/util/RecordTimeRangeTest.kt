package com.climus.climeet.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class RecordTimeRangeTest {
    @Test
    fun midnight_formatRecordTimeRange_displaysTwelveAm() {
        assertEquals(
            "AM 12:00 - AM 12:30",
            formatRecordTimeRange(LocalTime.MIDNIGHT, LocalTime.of(0, 30)),
        )
    }

    @Test
    fun noonBoundary_formatRecordTimeRange_switchesToPm() {
        assertEquals(
            "AM 11:59 - PM 12:00",
            formatRecordTimeRange(LocalTime.of(11, 59), LocalTime.NOON),
        )
    }

    @Test
    fun afternoon_formatRecordTimeRange_usesPaddedTwelveHourClock() {
        assertEquals(
            "PM 01:05 - PM 11:59",
            formatRecordTimeRange(LocalTime.of(13, 5), LocalTime.of(23, 59)),
        )
    }

    @Test
    fun midnightEnd_formatRecordTimeRange_preservesInputOrder() {
        assertEquals(
            "PM 11:59 - AM 12:00",
            formatRecordTimeRange(LocalTime.of(23, 59), LocalTime.MIDNIGHT),
        )
    }
}
