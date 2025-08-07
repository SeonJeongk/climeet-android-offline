package com.climus.climeet.presentation.ui.main.record.session.record.policy

/**
 * 루트 기록 개수를 화면 표시값에 맞춰 한 단계 변경합니다.
 *
 * `--`는 기록이 없다는 뜻입니다. 증가할 때는 `1`로 시작하고, `1` 또는 `0`에서 감소하면 `--`로 돌아갑니다.
 * `--` 감소는 저장된 기록을 삭제하는 정상 흐름에서 들어오지 않는 잘못된 입력입니다.
 */
internal fun adjustedRecordCount(
    current: String,
    increase: Boolean,
): String =
    if (increase) {
        if (current == "--") "1" else (current.toInt() + 1).toString()
    } else {
        if (current == "1" || current == "0") "--" else (current.toInt() - 1).toString()
    }
