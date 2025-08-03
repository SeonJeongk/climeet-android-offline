package com.climus.climeet.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 진행 중인 운동 한 건을 기기에 저장한 값입니다.
 *
 * [time]이 비어 있으면 계속 수정할 수 있는 기록입니다.
 * [time]에 값이 있으면 운동을 끝내고 서버 전송을 시도한 기록입니다.
 * 전송에 실패하면 이 값을 남겨 다음 실행에서 복구합니다.
 */
@Entity(tableName = "climbing_record")
data class ClimbingRecordData(
    @PrimaryKey(autoGenerate = false) val id: Int = 0,
    val gymId: Long = 0,
    val gymName: String = "",
    val date: String = "",
    val time: String = "",
    val avgDifficulty: Int = 0,
)
