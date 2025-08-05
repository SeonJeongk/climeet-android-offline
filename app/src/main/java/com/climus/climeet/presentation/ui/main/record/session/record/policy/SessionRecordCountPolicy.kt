package com.climus.climeet.presentation.ui.main.record.session.record

internal fun adjustedRecordCount(
    current: String,
    increase: Boolean,
): String =
    if (increase) {
        if (current == "--") "1" else (current.toInt() + 1).toString()
    } else {
        if (current == "1" || current == "0") "--" else (current.toInt() - 1).toString()
    }
