package com.climus.climeet.data.model

import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import retrofit2.Response

sealed class BaseState<out T> {
    data class Success<out T>(
        val body: T,
    ) : BaseState<T>()

    data class Error(
        val msg: String,
        val code: String,
        val cause: Throwable? = null,
    ) : BaseState<Nothing>()
}

suspend fun <T> runRemote(block: suspend () -> Response<T>): BaseState<T> =
    try {
        val response = block()
        if (response.isSuccessful) {
            response.body()?.let {
                BaseState.Success(it)
            } ?: run {
                BaseState.Error("응답이 비어있습니다", "EMPTY")
            }
        } else {
            val error = Gson().fromJson(response.errorBody()?.string(), ErrorResponse::class.java)
            BaseState.Error(error.message, error.code)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        BaseState.Error("네트워크 통신 에러", "EMPTY", e)
    }
