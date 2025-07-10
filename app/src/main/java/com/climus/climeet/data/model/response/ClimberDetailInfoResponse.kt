package com.climus.climeet.data.model.response

data class ClimberDetailInfoResponse(
    val page: Int,
    val hasNext: Boolean,
    val result: List<ClimberDetailInfoItem>,
)

data class ClimberDetailInfoItem(
    val userId: Long,
    val climberName: String,
    val profileImgUrl: String,
    val followerCount: Long,
    val isFollower: Boolean,
)
