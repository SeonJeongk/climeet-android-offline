package com.climus.climeet.presentation.ui.main.record.session.record

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRecordCountPolicyTest {
    @Test
    fun emptyDisplay_incrementThenDecrement_preservesTransitions() {
        var count = "--"
        for (expected in listOf("1", "2", "3")) {
            count = adjustedRecordCount(count, true)
            assertEquals(expected, count)
        }
        for (expected in listOf("2", "1", "--")) {
            count = adjustedRecordCount(count, false)
            assertEquals(expected, count)
        }
        assertEquals("1", adjustedRecordCount(count, true))
    }

    @Test
    fun emptyOrNumericCount_increase_returnsIncrementedDisplay() {
        assertEquals("1", adjustedRecordCount("--", true))
        assertEquals("1", adjustedRecordCount("0", true))
        assertEquals("2", adjustedRecordCount("1", true))
        assertEquals("13", adjustedRecordCount("12", true))
    }

    @Test
    fun numericCount_decrease_returnsDecrementedDisplay() {
        assertEquals("--", adjustedRecordCount("0", false))
        assertEquals("--", adjustedRecordCount("1", false))
        assertEquals("1", adjustedRecordCount("2", false))
        assertEquals("11", adjustedRecordCount("12", false))
    }

    @Test(expected = NumberFormatException::class)
    fun emptyDisplay_decrease_throwsNumberFormatException() {
        adjustedRecordCount("--", false)
    }
}
