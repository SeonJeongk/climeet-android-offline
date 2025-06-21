package com.climus.climeet.presentation.ui.main.record.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CreateRecordDataTest {

    @Test
    fun `31일보다 짧은 이전 달은 마지막 날짜로 보정한다`() {
        val result = initialRecordDate(LocalDate.of(2026, 7, 31))

        assertEquals(LocalDate.of(2026, 6, 30), result)
    }

    @Test
    fun `윤년 3월 31일의 이전 달은 2월 29일이다`() {
        val result = initialRecordDate(LocalDate.of(2024, 3, 31))

        assertEquals(LocalDate.of(2024, 2, 29), result)
    }

    @Test
    fun `1월의 이전 달은 이전 연도 12월이다`() {
        val result = initialRecordDate(LocalDate.of(2026, 1, 31))

        assertEquals(LocalDate.of(2025, 12, 31), result)
    }
}
