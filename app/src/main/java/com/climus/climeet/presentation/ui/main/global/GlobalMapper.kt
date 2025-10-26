package com.climus.climeet.presentation.ui.main.global

import com.climus.climeet.R
import com.climus.climeet.data.model.response.ClimberDetailInfoItem
import com.climus.climeet.data.model.response.DifficultyItem
import com.climus.climeet.data.model.response.GetAnnouncementResponse
import com.climus.climeet.data.model.response.GymListToFollowItem
import com.climus.climeet.data.model.response.RouteItem
import com.climus.climeet.data.model.response.SectorItem
import com.climus.climeet.data.model.response.UserFollowSimpleResponse
import com.climus.climeet.data.model.response.UserFollowerInfoResponse
import com.climus.climeet.data.model.response.UserFollowingInfoResponse
import com.climus.climeet.data.model.response.UserHomeGymSimpleResponse
import com.climus.climeet.data.repository.route.CachedRoute
import com.climus.climeet.data.repository.route.RouteDifficulty
import com.climus.climeet.data.repository.route.RouteSector
import com.climus.climeet.presentation.ui.main.global.climerprofile.model.ProfileHomeGymUiData
import com.climus.climeet.presentation.ui.main.global.searchprofile.model.SearchProfileUiData
import com.climus.climeet.presentation.ui.main.global.searchprofile.model.UserFollowingUiData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.GymLevelUiData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.RouteUiData
import com.climus.climeet.presentation.ui.main.global.selectsector.model.SectorNameUiData
import com.climus.climeet.presentation.ui.main.mypage.announce.model.AnnouncementUiData
import com.climus.climeet.presentation.ui.main.mypage.follow.model.FollowUiData
import com.climus.climeet.presentation.ui.main.mypage.follow.model.FollowingUiData
import com.climus.climeet.presentation.util.Constants

fun SectorItem.toSectorNameUiData(onClickListener: (SectorNameUiData) -> Unit) =
    SectorNameUiData(
        sectorId = sectorId,
        name = name,
        floor = floor,
        onClickListener = onClickListener,
    )

fun DifficultyItem.toGymLevelUiData(onClickListener: (GymLevelUiData) -> Unit) =
    GymLevelUiData(
        levelName = gymDifficultyName,
        levelColor = gymDifficultyColor,
        difficulty = difficulty,
        onClickListener = onClickListener,
    )

fun RouteItem.toRouteUiData(onClickListener: (RouteUiData) -> Unit): RouteUiData {
    val holdColor = Constants.holdColor[holdColor] ?: run {
        R.drawable.ic_white_hold
    }

    return RouteUiData(
        routeId = routeId,
        sectorId = sectorId,
        sectorName = sectorName,
        difficulty = difficulty,
        gymLevelName = gymDifficultyName,
        gymLevelColor = gymDifficultyColor,
        climeetLevelName = climeetDifficultyName,
        routeImg = routeImageUrl,
        holdImg = holdColor,
        onClickListener = onClickListener,
    )
}

fun RouteSector.toSectorNameUiData(onClickListener: (SectorNameUiData) -> Unit) =
    SectorNameUiData(
        sectorId = sectorId,
        name = name,
        floor = floor,
        onClickListener = onClickListener,
    )

fun RouteDifficulty.toGymLevelUiData(onClickListener: (GymLevelUiData) -> Unit) =
    GymLevelUiData(
        levelName = gymDifficultyName.toLevelDisplayName(),
        levelColor = gymDifficultyColor,
        difficulty = difficulty,
        onClickListener = onClickListener,
    )

/** 데이터가 교체되어 목록에서 사라진 루트는 선택을 해제합니다. */
fun RouteUiData.keptIn(routes: List<RouteUiData>): RouteUiData =
    if (routes.any { it.routeId == routeId }) this else RouteUiData {}

private fun String.toLevelDisplayName() = if (this == "V0") "VB" else this

fun CachedRoute.toRouteUiData(onClickListener: (RouteUiData) -> Unit): RouteUiData =
    RouteUiData(
        routeId = routeId,
        sectorId = sectorId,
        sectorName = sectorName,
        difficulty = difficulty,
        gymLevelName = gymDifficultyName.toLevelDisplayName(),
        gymLevelColor = gymDifficultyColor,
        climeetLevelName = climeetDifficultyName.toLevelDisplayName(),
        routeImg = routeImageUrl,
        holdImg = Constants.holdColor[holdColor] ?: R.drawable.ic_white_hold,
        onClickListener = onClickListener,
    )

fun GymListToFollowItem.toSearchProfileUiData(
    keyword: String,
    navigateToProfile: (Long) -> Unit,
    follow: (Long) -> Unit,
    unFollow: (Long) -> Unit,
) = SearchProfileUiData(
    id = gymId,
    imgUrl = profileImageUrl,
    followers = follower,
    name = name,
    keyword = keyword,
    isGym = true,
    navigateToProfile = navigateToProfile,
    follow = follow,
    unFollow = unFollow,
    isFollowing = isFollow,
)

fun ClimberDetailInfoItem.toSearchProfileUiData(
    keyword: String,
    navigateToProfile: (Long) -> Unit,
    follow: (Long) -> Unit,
    unFollow: (Long) -> Unit,
) = SearchProfileUiData(
    id = userId,
    imgUrl = profileImgUrl,
    followers = followerCount.toInt(),
    name = climberName,
    keyword = keyword,
    isGym = false,
    navigateToProfile = navigateToProfile,
    follow = follow,
    unFollow = unFollow,
    isFollowing = isFollower,
)

fun UserFollowSimpleResponse.toUserFollowingUiData(
    navigateToProfile: (Long) -> Unit,
    follow: (Long) -> Unit,
    unFollow: (Long) -> Unit,
) = UserFollowingUiData(
    id = userId,
    imgUrl = userProfileUrl,
    followers = followerCount.toInt(),
    name = userName,
    navigateToProfile = navigateToProfile,
    follow = follow,
    unFollow = unFollow,
    isFollowing = true,
)

fun UserHomeGymSimpleResponse.toUserFollowingUiData(
    navigateToProfile: (Long) -> Unit,
    follow: (Long) -> Unit,
    unFollow: (Long) -> Unit,
) = UserFollowingUiData(
    id = gymId,
    imgUrl = gymProfileUrl,
    followers = followerCount,
    name = gymName,
    navigateToProfile = navigateToProfile,
    follow = follow,
    unFollow = unFollow,
    isFollowing = true,
)

fun UserFollowerInfoResponse.toUserFollowerUiData(
    navigateToProfile: (Long) -> Unit,
    follow: (Long) -> Unit,
    unFollow: (Long) -> Unit,
) = FollowUiData(
    userId = userId,
    userName = userName,
    profileImageUrl = userProfileUrl,
    followerCount = followerCount,
    followingCount = followingCount,
    isFollowing = isFollower,
    navigateToProfile = navigateToProfile,
    follow = follow,
    unFollow = unFollow,
)

fun UserFollowingInfoResponse.toUserFollowingUiData(
    navigateToProfile: (Long) -> Unit,
    follow: (Long) -> Unit,
    unFollow: (Long) -> Unit,
) = FollowingUiData(
    userId = userId,
    userName = userName,
    profileImageUrl = profileImgUrl,
    followerCount = followerCount,
    followingCount = followingCount,
    isFollowing = isFollower,
    navigateToProfile = navigateToProfile,
    follow = follow,
    unFollow = unFollow,
)

fun UserHomeGymSimpleResponse.toProfileHomeGymUiData(onClickListener: (Long) -> Unit) =
    ProfileHomeGymUiData(
        gymId = gymId,
        profileImg = gymProfileUrl,
        name = gymName,
        followerString = "팔로워 $followerCount",
        onClickListener = onClickListener,
    )

fun GetAnnouncementResponse.toAnnouncementUiData() =
    AnnouncementUiData(
        boardId = boardId,
        createdAt = createdAt,
        likeCount = likeCount,
        title = title,
        content = content,
        profileImageUrl = profileImageUrl,
        image = image,
    )
