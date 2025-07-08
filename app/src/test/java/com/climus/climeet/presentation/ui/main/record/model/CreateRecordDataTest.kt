package com.climus.climeet.presentation.ui.main.record.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CreateRecordDataTest {
    @Test
    fun shorterPreviousMonth_initialRecordDate_clampsToLastDay() {
        val result = initialRecordDate(LocalDate.of(2026, 7, 31))

        assertEquals(LocalDate.of(2026, 6, 30), result)
    }

    @Test
    fun leapYearMarchEnd_initialRecordDate_returnsFebruary29() {
        val result = initialRecordDate(LocalDate.of(2024, 3, 31))

        assertEquals(LocalDate.of(2024, 2, 29), result)
    }

    @Test
    fun january_initialRecordDate_returnsPreviousDecember() {
        val result = initialRecordDate(LocalDate.of(2026, 1, 31))

        assertEquals(LocalDate.of(2025, 12, 31), result)
    }
}
