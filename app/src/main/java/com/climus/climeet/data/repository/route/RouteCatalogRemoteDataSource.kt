package com.climus.climeet.data.repository.route

import com.climus.climeet.data.model.BaseState
import com.climus.climeet.data.model.ErrorType
import com.climus.climeet.data.model.request.GetGymRouteInfoRequest
import com.climus.climeet.data.model.response.GetGymFilteringKeyResponse
import com.climus.climeet.data.model.response.RouteItem
import com.climus.climeet.data.remote.MainApi
import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject

sealed interface FilteringKeyResponse {
    data class Modified(
        val body: GetGymFilteringKeyResponse,
        val etag: String?,
        val cacheControl: String?,
    ) : FilteringKeyResponse

    data class NotModified(
        val cacheControl: String?,
    ) : FilteringKeyResponse

    data class Failed(
        val error: BaseState.Error,
    ) : FilteringKeyResponse
}

sealed interface RoutePageResponse {
    data class Success(
        val page: Int,
        val hasNext: Boolean,
        val routes: List<RouteItem>,
    ) : RoutePageResponse

    data class Failed(
        val error: BaseState.Error,
    ) : RoutePageResponse
}

interface RouteCatalogRemoteDataSource {
    suspend fun filteringKey(
        key: RouteCatalogKey,
        etag: String?,
    ): FilteringKeyResponse

    suspend fun routePage(
        key: RouteCatalogKey,
        floor: Int,
        page: Int,
    ): RoutePageResponse
}

class RetrofitRouteCatalogRemoteDataSource @Inject constructor(
    private val api: MainApi,
) : RouteCatalogRemoteDataSource {
    override suspend fun filteringKey(
        key: RouteCatalogKey,
        etag: String?,
    ): FilteringKeyResponse =
        remoteCall(FILTERING_KEY_TIMEOUT_MS, FilteringKeyResponse::Failed) {
            val response = api.getGymFilteringKeyTime(key.gymId, key.queryDate.toString(), etag)
            when (response.code()) {
                304 -> FilteringKeyResponse.NotModified(response.headers()["Cache-Control"])
                in 200..299 -> {
                    val body = response.body()
                        ?: return@remoteCall FilteringKeyResponse.Failed(emptyBodyError(response))
                    FilteringKeyResponse.Modified(
                        body = body,
                        etag = response.headers()["ETag"],
                        cacheControl = response.headers()["Cache-Control"],
                    )
                }

                else -> FilteringKeyResponse.Failed(response.toRemoteError())
            }
        }

    override suspend fun routePage(
        key: RouteCatalogKey,
        floor: Int,
        page: Int,
    ): RoutePageResponse =
        remoteCall(ROUTE_PAGE_TIMEOUT_MS, RoutePageResponse::Failed) {
            val response = api.getGymRouteInfoList(
                key.gymId,
                GetGymRouteInfoRequest(
                    page = page,
                    size = ROUTE_PAGE_SIZE,
                    floor = floor,
                    timePoint = key.queryDate.toString(),
                ),
            )
            if (!response.isSuccessful) return@remoteCall RoutePageResponse.Failed(response.toRemoteError())
            val body = response.body() ?: return@remoteCall RoutePageResponse.Failed(emptyBodyError(response))
            RoutePageResponse.Success(body.page, body.hasNext, body.result)
        }

    private suspend fun <T> remoteCall(
        timeoutMs: Long,
        failure: (BaseState.Error) -> T,
        block: suspend () -> T,
    ): T =
        try {
            withTimeout(timeoutMs) { block() }
        } catch (_: TimeoutCancellationException) {
            failure(timeoutError())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failure(
                when (error) {
                    is SocketTimeoutException -> timeoutError()
                    is IOException -> transportError(error)
                    is JsonParseException -> contractError(error)
                    else -> unknownError(error)
                },
            )
        }

    private fun transportError(error: Throwable) =
        BaseState.Error("네트워크 통신 에러", "TRANSPORT", error, ErrorType.TRANSPORT)

    private fun contractError(error: Throwable) =
        BaseState.Error("응답 형식이 올바르지 않습니다", "CONTRACT", error, ErrorType.CONTRACT)

    private fun unknownError(error: Throwable) = BaseState.Error("네트워크 통신 에러", "UNKNOWN", error, ErrorType.UNKNOWN)

    private fun timeoutError() = BaseState.Error("요청 시간이 초과되었습니다", "TIMEOUT", type = ErrorType.TIMEOUT)

    private fun emptyBodyError(response: Response<*>) =
        BaseState.Error(
            "응답이 비어있습니다",
            "EMPTY",
            type = ErrorType.EMPTY_BODY,
            httpStatus = response.code(),
        )

    private fun Response<*>.toRemoteError(): BaseState.Error =
        try {
            val parsed = Gson().fromJson(errorBody()?.string(), ErrorBody::class.java)
            BaseState.Error(
                parsed?.message ?: "네트워크 통신 에러",
                parsed?.code ?: "HTTP_${code()}",
                type = ErrorType.HTTP,
                httpStatus = code(),
            )
        } catch (error: Exception) {
            BaseState.Error(
                "네트워크 통신 에러",
                "HTTP_${code()}",
                error,
                ErrorType.HTTP,
                code(),
            )
        }

    private data class ErrorBody(
        val message: String?,
        val code: String?,
    )

    private companion object {
        const val FILTERING_KEY_TIMEOUT_MS = 5_000L
        const val ROUTE_PAGE_TIMEOUT_MS = 10_000L
        const val ROUTE_PAGE_SIZE = 10
    }
}
