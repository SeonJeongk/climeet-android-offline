package com.climus.climeet.data.repository

import com.climus.climeet.data.local.ClimbingRecordData
import com.climus.climeet.data.local.RouteRecordData
import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.model.request.AddShortsCommentRequest
import com.climus.climeet.data.model.request.CreateGymProfileReviewRequest
import com.climus.climeet.data.model.request.CreateTimerClimbingRecordRequest
import com.climus.climeet.data.model.request.GetGymRouteInfoRequest
import com.climus.climeet.data.model.request.GymServiceUpdateRequest
import com.climus.climeet.data.model.request.ImgUrlRequest
import com.climus.climeet.data.model.request.NotificationUpdateRequest
import com.climus.climeet.data.model.request.PatchAdminNameRequest
import com.climus.climeet.data.model.request.ShortsDetailRequest
import com.climus.climeet.data.model.request.UpdateGymRouteVersionRequest
import com.climus.climeet.data.model.response.BannerDetailInfoResponse
import com.climus.climeet.data.model.response.BestClearClimberSimpleResponse
import com.climus.climeet.data.model.response.BestFollowGymSimpleResponse
import com.climus.climeet.data.model.response.BestLevelCimberSimpleResponse
import com.climus.climeet.data.model.response.BestRecordGymDetailInfoResponse
import com.climus.climeet.data.model.response.BestRouteDetailInfoResponse
import com.climus.climeet.data.model.response.BestTimeClimberSimpleResponse
import com.climus.climeet.data.model.response.ClimbedGym
import com.climus.climeet.data.model.response.ClimberDetailInfoResponse
import com.climus.climeet.data.model.response.GetAnnouncementDetailResponse
import com.climus.climeet.data.model.response.GetAnnouncementResponse
import com.climus.climeet.data.model.response.GetClimberPrivacySettingResponse
import com.climus.climeet.data.model.response.GetClimberProfileStatisticsResponse
import com.climus.climeet.data.model.response.GetClimberProfileTargetGymStatisticsResponse
import com.climus.climeet.data.model.response.GetGymFilteringKeyResponse
import com.climus.climeet.data.model.response.GetGymListToFollowResponse
import com.climus.climeet.data.model.response.GetGymProfileResponse
import com.climus.climeet.data.model.response.GetGymProfileReviewResponse
import com.climus.climeet.data.model.response.GetGymRouteInfoResponse
import com.climus.climeet.data.model.response.GetGymSkillDistributionResponse
import com.climus.climeet.data.model.response.GetMyShortsCommentsResponse
import com.climus.climeet.data.model.response.GetMyStatsTargetGymMonthResponse
import com.climus.climeet.data.model.response.GetNotificationStatesResponse
import com.climus.climeet.data.model.response.GetRouteFindingData
import com.climus.climeet.data.model.response.GetSelectDateRecordResponse
import com.climus.climeet.data.model.response.GetUserInfoResponse
import com.climus.climeet.data.model.response.GymCompleteBestClimberResponse
import com.climus.climeet.data.model.response.GymLevelBestClimberResponse
import com.climus.climeet.data.model.response.GymProfileTabInfoResponse
import com.climus.climeet.data.model.response.GymProfileTopInfoResponse
import com.climus.climeet.data.model.response.GymTimeBestClimberResponse
import com.climus.climeet.data.model.response.GymWeekStatsResponse
import com.climus.climeet.data.model.response.MyPageProfileResponse
import com.climus.climeet.data.model.response.MyStatsMonthResponse
import com.climus.climeet.data.model.response.SearchAvailableGymResponse
import com.climus.climeet.data.model.response.SearchGymResponse
import com.climus.climeet.data.model.response.ShortsListResponse
import com.climus.climeet.data.model.response.ShortsMainCommentItem
import com.climus.climeet.data.model.response.ShortsMainCommentResponse
import com.climus.climeet.data.model.response.ShortsSubCommentResponse
import com.climus.climeet.data.model.response.ShortsUpdatedFollowResponse
import com.climus.climeet.data.model.response.UploadImgResponse
import com.climus.climeet.data.model.response.UserFollowSimpleResponse
import com.climus.climeet.data.model.response.UserFollowerInfoResponse
import com.climus.climeet.data.model.response.UserFollowingInfoResponse
import com.climus.climeet.data.model.response.UserHomeGymDetailResponse
import com.climus.climeet.data.model.response.UserHomeGymSimpleResponse
import com.climus.climeet.data.model.response.UserProfileInfoResponse
import com.climus.climeet.data.model.response.UserShortsSortType
import com.climus.climeet.data.model.response.UserShortsVisibilityType
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Query

interface MainRepository {
    suspend fun climberNickNameCheck(nickName: String): BaseState<Boolean>

    suspend fun uploadFile(file: MultipartBody.Part): BaseState<UploadImgResponse>

    suspend fun searchGym(
        gymName: String,
        page: Int,
        size: Int,
    ): BaseState<SearchGymResponse>

    suspend fun getUserFollowing(
        @Query("userId") userId: Long?,
        @Query("userCategory") userCategory: String,
    ): BaseState<List<UserFollowingInfoResponse>>

    suspend fun getUserFollowers(
        @Query("userId") userId: Long?,
        @Query("userCategory") userCategory: String,
    ): BaseState<List<UserFollowerInfoResponse>>

    suspend fun searchAvailableGym(
        gymName: String,
        page: Int,
        size: Int,
    ): BaseState<SearchAvailableGymResponse>

    suspend fun getUserProfile(): BaseState<UserProfileInfoResponse>

    suspend fun getRecentShorts(
        page: Int,
        size: Int,
        filter: Map<String, Long>,
    ): BaseState<ShortsListResponse>

    suspend fun getPopularShorts(
        page: Int,
        size: Int,
        filter: Map<String, Long>,
    ): BaseState<ShortsListResponse>

    suspend fun getGymsFollowing(): BaseState<List<UserHomeGymDetailResponse>>

    suspend fun getShortsUpdatedFollow(): BaseState<List<ShortsUpdatedFollowResponse>>

    suspend fun findBannerListBetweenDates(): BaseState<List<BannerDetailInfoResponse>>

    suspend fun findClimberRankingOrderClearCount(): BaseState<List<BestClearClimberSimpleResponse>>

    suspend fun findClimberRankingOrderTime(): BaseState<List<BestTimeClimberSimpleResponse>>

    suspend fun findClimberRankingOrderLevel(): BaseState<List<BestLevelCimberSimpleResponse>>

    suspend fun findGymRankingOrderFollowCount(): BaseState<List<BestFollowGymSimpleResponse>>

    suspend fun findGymRankingListOrderSelectionCount(): BaseState<List<BestRecordGymDetailInfoResponse>>

    suspend fun findRouteRankingOrderSelectionCount(): BaseState<List<BestRouteDetailInfoResponse>>

    suspend fun getClimberFollowing(): BaseState<List<UserFollowSimpleResponse>>

    suspend fun getHomeGyms(): BaseState<List<UserHomeGymSimpleResponse>>

    suspend fun getClimberPrivacySetting(
        @Query("climberId") climberId: Int,
    ): BaseState<GetClimberPrivacySettingResponse>

    suspend fun getClimberSearchingList(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("climberName") climberName: String,
    ): BaseState<ClimberDetailInfoResponse>

    suspend fun getSelectDateRecord(
        startDate: String,
        endDate: String,
    ): BaseState<List<GetSelectDateRecordResponse>>

    suspend fun followUser(followingUserId: Long): BaseState<ResponseBody>

    suspend fun unfollowUser(followingUserId: Long): BaseState<ResponseBody>

    suspend fun followGym(gymId: Long): BaseState<ResponseBody>

    suspend fun unFollowGym(gymId: Long): BaseState<ResponseBody>

    suspend fun getGymFilteringKey(gymId: Long): BaseState<GetGymFilteringKeyResponse>

    suspend fun getGymFilteringKeyTime(
        gymId: Long,
        timePoint: String,
    ): BaseState<GetGymFilteringKeyResponse>

    suspend fun getGymRouteFindingData(timePoint: String): BaseState<GetRouteFindingData>

    suspend fun getGymRouteInfoList(
        gymId: Long,
        body: GetGymRouteInfoRequest,
    ): BaseState<GetGymRouteInfoResponse>

    suspend fun updateGymRouteVersion(body: UpdateGymRouteVersionRequest): BaseState<ResponseBody>

    suspend fun createTimerClimbingRecord(body: CreateTimerClimbingRecordRequest): BaseState<ResponseBody>

    suspend fun getGymProfile(gymId: Long): BaseState<GetGymProfileResponse>

    suspend fun uploadShorts(
        video: MultipartBody.Part?,
        body: ShortsDetailRequest,
    ): BaseState<Unit>

    suspend fun patchBookMark(shortsId: Long): BaseState<Unit>

    suspend fun patchFavorite(shortsId: Long): BaseState<Unit>

    suspend fun getShortsSubCommentList(
        shortsId: Long,
        parentCommentId: Long,
        page: Int,
        size: Int,
    ): BaseState<ShortsSubCommentResponse>

    suspend fun getShortsCommentList(
        shortsId: Long,
        page: Int,
        size: Int,
    ): BaseState<ShortsMainCommentResponse>

    suspend fun addShortsComment(
        shortsId: Long,
        filter: Map<String, Long>,
        body: AddShortsCommentRequest,
    ): BaseState<ShortsMainCommentItem>

    suspend fun patchShortsCommentInteraction(
        shortsCommentId: Long,
        isLike: Boolean,
        isDislike: Boolean,
    ): BaseState<String>

    suspend fun getGymProfileTopInfo(gymId: Long): BaseState<GymProfileTopInfoResponse>

    suspend fun getGymProfileTabInfo(gymId: Long): BaseState<GymProfileTabInfoResponse>

    suspend fun getMyStatsMonth(
        year: Int,
        month: Int,
    ): BaseState<MyStatsMonthResponse>

    suspend fun getGymReview(
        gymId: Long,
        page: Int,
        size: Int,
    ): BaseState<GetGymProfileReviewResponse>

    suspend fun createGymReview(
        gymId: Long,
        body: CreateGymProfileReviewRequest,
    ): BaseState<ResponseBody>

    suspend fun editGymReview(
        gymId: Long,
        body: CreateGymProfileReviewRequest,
    ): BaseState<ResponseBody>

    suspend fun getGymClimberRankingOrderClearCount(gymId: Long): BaseState<List<GymCompleteBestClimberResponse>>

    suspend fun getGymClimberRankingOrderLevel(gymId: Long): BaseState<List<GymLevelBestClimberResponse>>

    suspend fun getGymClimberRankingOrderTime(gymId: Long): BaseState<List<GymTimeBestClimberResponse>>

    suspend fun getGymSkillDistribution(gymId: Long): BaseState<List<GetGymSkillDistributionResponse>>

    suspend fun getMyGymSkill(gymId: Long): BaseState<ResponseBody>

    suspend fun getGymStatsWeek(gymId: Long): BaseState<GymWeekStatsResponse>

    suspend fun getMyPageProfile(): BaseState<MyPageProfileResponse>

    suspend fun editHomeGymPrivacy(): BaseState<ResponseBody>

    suspend fun editAvgCompletePrivacy(): BaseState<ResponseBody>

    suspend fun editAvgCompleteLevelPrivacy(): BaseState<ResponseBody>

    suspend fun updateUserProfileImage(image: MultipartBody.Part): BaseState<ResponseBody>

    suspend fun updateUserName(name: String): BaseState<ResponseBody>

    suspend fun updateAdminProfileImage(body: ImgUrlRequest): BaseState<ResponseBody>

    suspend fun updateAdminName(body: PatchAdminNameRequest): BaseState<ResponseBody>

    suspend fun updateAdminBackgroundImage(body: ImgUrlRequest): BaseState<ResponseBody>

    suspend fun updateGymService(body: GymServiceUpdateRequest): BaseState<ResponseBody>

    suspend fun getNotificationStates(): BaseState<GetNotificationStatesResponse>

    suspend fun updateNotification(body: NotificationUpdateRequest): BaseState<ResponseBody>

    suspend fun getMyShortsComments(
        page: Int,
        size: Int,
    ): BaseState<GetMyShortsCommentsResponse>

    suspend fun insert(climbingRecordData: ClimbingRecordData)

    suspend fun update(climbingRecordData: ClimbingRecordData)

    suspend fun delete(climbingRecordData: ClimbingRecordData)

    suspend fun deleteAllRecord()

    suspend fun getAllRecord(): List<ClimbingRecordData>

    suspend fun getRecord(id: Int): ClimbingRecordData?

    /**
     * 운동 종료 시간을 저장하고 현재 루트 기록을 서버 전송용 값으로 묶습니다.
     *
     * 두 작업은 함께 처리되며, 저장된 운동 기록이 없으면 `null`을 반환합니다.
     */
    suspend fun finalizeTimerClimbingRecord(
        id: Int,
        time: String,
    ): CreateTimerClimbingRecordRequest?

    suspend fun clearTimerClimbingRecordDraft()

    suspend fun insert(routeRecord: RouteRecordData)

    suspend fun update(routeRecord: RouteRecordData)

    suspend fun delete(routeRecord: RouteRecordData)

    suspend fun deleteAllRoute()

    suspend fun deleteById(id: Int)

    suspend fun getAllRoute(): List<RouteRecordData>

    suspend fun getRouteById(id: Int): RouteRecordData

    suspend fun findExistRoute(
        sectorId: Long,
        routeId: Long,
    ): RouteRecordData?

    suspend fun getAverageDifficultyOfCompleted(): Double?

    suspend fun getAllLevelRecord(): List<RouteRecordData>

    suspend fun getSuccessCount(level: String): Int

    suspend fun getAttemptCount(level: String): Int

    suspend fun getGymListToFollow(
        gymname: String,
        page: Int,
        size: Int,
    ): BaseState<GetGymListToFollowResponse>

    suspend fun getClimberProfileStatistics(userId: Long): BaseState<GetClimberProfileStatisticsResponse>

    suspend fun getClimberProfileTargetGymStatistics(
        userId: Long,
        gymId: Long,
    ): BaseState<GetClimberProfileTargetGymStatisticsResponse>

    suspend fun getUserClimbedGymList(userId: Int): BaseState<List<ClimbedGym>>

    suspend fun getMyStatsTargetGymMonth(
        gymId: Long,
        year: Int,
        month: Int,
    ): BaseState<GetMyStatsTargetGymMonthResponse>

    suspend fun getMyClimbedGymList(
        year: Int,
        month: Int,
    ): BaseState<List<ClimbedGym>>

    suspend fun getUserShorts(
        uploaderId: Long,
        page: Int,
        size: Int,
        sortType: UserShortsSortType,
    ): BaseState<ShortsListResponse>

    suspend fun getShortsById(shortsId: Long): BaseState<ShortsListResponse>

    suspend fun getMyShorts(
        shortsVisibility: UserShortsVisibilityType,
        page: Int,
        size: Int,
    ): BaseState<ShortsListResponse>

    suspend fun getShortsLikedList(
        page: Int,
        size: Int,
    ): BaseState<ShortsListResponse>

    suspend fun getShortsBookmarkedList(
        page: Int,
        size: Int,
    ): BaseState<ShortsListResponse>

    suspend fun getUserHomeGyms(userId: Long): BaseState<List<UserHomeGymSimpleResponse>>

    suspend fun getUserInfo(userId: Long): BaseState<GetUserInfoResponse>

    suspend fun getAnnouncement(): BaseState<List<GetAnnouncementResponse>>

    suspend fun getAnnouncementDetail(boardId: Long): BaseState<GetAnnouncementDetailResponse>

    suspend fun updateAnnouncementLike(boardId: Long): BaseState<ResponseBody>

    suspend fun updateAnnouncementUnlike(boardId: Long): BaseState<ResponseBody>

    suspend fun getAdminGymId(): BaseState<Int>
}
