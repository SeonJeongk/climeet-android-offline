package com.climus.climeet.data.model

import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

enum class ErrorType {
    UNKNOWN,
    TIMEOUT,
    TRANSPORT,
    HTTP,
    CONTRACT,
    EMPTY_BODY,
}

sealed class BaseState<out T> {
    data class Success<out T>(
        val body: T,
    ) : BaseState<T>()

    data class Error(
        val msg: String,
        val code: String,
        val cause: Throwable? = null,
        val type: ErrorType = ErrorType.UNKNOWN,
        val httpStatus: Int? = null,
    ) : BaseState<Nothing>() {
        /** 오류의 재시도 가능 여부입니다. 실제 재전송의 안전성과 실행 시점은 호출부에서 판단합니다. */
        val retryable: Boolean
            get() = when (type) {
                ErrorType.HTTP ->
                    httpStatus == 408 || httpStatus == 429 || (httpStatus != null && httpStatus in 500..599)

                ErrorType.TIMEOUT, ErrorType.TRANSPORT -> true
                else -> false
            }
    }
}

suspend fun <T> runRemote(block: suspend () -> Response<T>): BaseState<T> =
    try {
        val response = block()
        if (response.isSuccessful) {
            response.body()?.let {
                BaseState.Success(it)
            } ?: run {
                BaseState.Error("응답이 비어있습니다", "EMPTY", type = ErrorType.EMPTY_BODY, httpStatus = response.code())
            }
        } else {
            response.toRemoteError()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val type = when (e) {
            is SocketTimeoutException -> ErrorType.TIMEOUT
            is JsonParseException -> ErrorType.CONTRACT
            is IOException -> ErrorType.TRANSPORT
            else -> ErrorType.UNKNOWN
        }
        BaseState.Error(
            "네트워크 통신 에러",
            "EMPTY",
            e,
            type = type,
        )
    }

private fun Response<*>.toRemoteError(): BaseState.Error =
    try {
        val error = Gson().fromJson(errorBody()?.string(), ErrorResponse::class.java)
        val message: String? = error?.message
        val errorCode: String? = error?.code
        if (message == null || errorCode == null) {
            throw JsonParseException("Missing HTTP error message or code")
        }
        BaseState.Error(
            message,
            errorCode,
            type = ErrorType.HTTP,
            httpStatus = code(),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 오류 본문을 읽지 못해도 이미 받은 HTTP 상태로 재시도 여부를 판단합니다.
        BaseState.Error(
            "네트워크 통신 에러",
            "EMPTY",
            e,
            type = ErrorType.HTTP,
            httpStatus = code(),
        )
    }
