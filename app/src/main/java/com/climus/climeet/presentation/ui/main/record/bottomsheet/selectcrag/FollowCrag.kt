package com.climus.climeet.presentation.ui.main.record.bottomsheet.selectcrag

import com.climus.climeet.data.model.response.SearchAvailableGymItem
import com.climus.climeet.data.model.response.UserHomeGymSimpleResponse

data class FollowCrag(
    val id: Long,
    val imgUrl: String = "",
    val name: String = "",
    val keyword: String = "",
    var followers: Int = 0,
    var isFollowing: Boolean = false,
)

fun SearchAvailableGymItem.toFollowCrag(keyword: String) =
    FollowCrag(
        id = id,
        imgUrl = profileImageUrl,
        followers = follower,
        name = name,
        keyword = keyword,
    )

fun UserHomeGymSimpleResponse.toFollowCrag(keyword: String) =
    FollowCrag(
        id = gymId,
        imgUrl = gymProfileUrl,
        name = gymName,
        keyword = keyword,
        followers = 0,
        isFollowing = true,
    )
