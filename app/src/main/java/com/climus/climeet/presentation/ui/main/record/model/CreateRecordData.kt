package com.climus.climeet.presentation.ui.main.record.model

import com.climus.climeet.presentation.ui.main.record.bottomsheet.selectcrag.FollowCrag
import java.time.LocalDate
import java.time.LocalTime

internal fun initialRecordDate(today: LocalDate): LocalDate = today.minusMonths(1)

object CreateRecordData {
    val today = LocalDate.now()

    var selectedDate: LocalDate = initialRecordDate(today)
        private set

    var selectedStartTime: LocalTime = LocalTime.of(11, 0, 0)
        private set

    var selectedEndTime: LocalTime = LocalTime.of(11, 0, 0)
        private set

    var selectedCrag: FollowCrag? = FollowCrag(-1, "", "", "", 0, false)

    fun setSelectedDate(date: LocalDate) {
        selectedDate = date
    }

    fun setSelectedTime(
        start: LocalTime,
        end: LocalTime,
    ) {
        selectedStartTime = start
        selectedEndTime = end
    }

    fun setSelecetedCrag(crag: FollowCrag) {
        selectedCrag = crag
    }
}
