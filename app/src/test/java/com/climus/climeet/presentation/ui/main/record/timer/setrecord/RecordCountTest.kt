package com.climus.climeet.presentation.ui.main.record.timer.setrecord

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordCountTest {
    @Test
    fun `증가 시 빈 표시와 기존 숫자를 유지한다`() {
        assertEquals("1", adjustedRecordCount("--", true))
        assertEquals("1", adjustedRecordCount("0", true))
        assertEquals("2", adjustedRecordCount("1", true))
        assertEquals("13", adjustedRecordCount("12", true))
    }

    @Test
    fun `감소 시 0과 1은 빈 표시가 되고 나머지는 하나 줄어든다`() {
        assertEquals("--", adjustedRecordCount("0", false))
        assertEquals("--", adjustedRecordCount("1", false))
        assertEquals("1", adjustedRecordCount("2", false))
        assertEquals("11", adjustedRecordCount("12", false))
    }

    @Test(expected = NumberFormatException::class)
    fun `기존에 허용하지 않은 빈 표시 감소는 그대로 실패한다`() {
        adjustedRecordCount("--", false)
    }
}
