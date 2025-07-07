package com.climus.climeet.data.model

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class RunRemoteFailureTest {
    @Test
    fun unexpectedFailure_runRemote_preservesFallback() =
        runBlocking {
            val cause = IllegalStateException("invalid state")
            val error = runRemote<String> { throw cause } as BaseState.Error
            assertFallback(error)
            assertSame(cause, error.cause)
        }

    @Test
    fun incompleteError_runRemote_preservesFallback() =
        runBlocking {
            for (body in listOf("", "null", "{}", """{"code":"DENIED"}""", """{"message":"권한 없음"}""")) {
                val error = runRemote<String> {
                    Response.error(500, body.toResponseBody("application/json".toMediaType()))
                } as BaseState.Error
                assertFallback(error)
            }
        }

    @Test
    fun transportFailure_runRemote_preservesNetworkError() =
        runBlocking {
            val cause = IOException("offline")
            val error = runRemote<String> { throw cause } as BaseState.Error
            assertFallback(error)
            assertSame(cause, error.cause)
        }

    @Test
    fun malformedError_runRemote_preservesNetworkError() =
        runBlocking {
            val error = runRemote<String> {
                Response.error(500, "not-json".toResponseBody("application/json".toMediaType()))
            } as BaseState.Error
            assertFallback(error)
        }

    @Test
    fun serverError_runRemote_preservesServerMessageAndCode() =
        runBlocking {
            val body = """{"isSuccess":"false","code":"DENIED","message":"권한 없음"}"""
            assertEquals(
                BaseState.Error("권한 없음", "DENIED"),
                runRemote<String> { Response.error(403, body.toResponseBody("application/json".toMediaType())) },
            )
        }

    private fun assertFallback(error: BaseState.Error) {
        assertEquals("네트워크 통신 에러", error.msg)
        assertEquals("EMPTY", error.code)
        assertNotNull(error.cause)
    }
}
