package com.climus.climeet.data.model

import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class RunRemoteFailureTest {
    @Test
    fun unexpectedFailure_runRemote_preservesFallback() =
        runBlocking {
            val cause = IllegalStateException("invalid state")
            val error = runRemote<String> { throw cause } as BaseState.Error
            assertFallback(error)
            assertSame(cause, error.cause)
            assertEquals(ErrorType.UNKNOWN, error.type)
            assertFalse(error.retryable)
        }

    @Test
    fun unusableHttpErrorBody_runRemote_preservesStatusAndRetryPolicy() =
        runBlocking {
            for ((status, retryable) in listOf(503 to true, 403 to false)) {
                for (body in listOf(
                    "not-json",
                    "<html>unavailable</html>",
                    "",
                    "null",
                    "{}",
                    """{"code":"DENIED"}""",
                    """{"message":"권한 없음"}""",
                )) {
                    var calls = 0
                    val error = runRemote<String> {
                        calls++
                        Response.error(status, body.toResponseBody("application/json".toMediaType()))
                    } as BaseState.Error
                    val case = "status=$status, body=$body"
                    assertEquals(case, ErrorType.HTTP, error.type)
                    assertEquals(case, status, error.httpStatus)
                    assertEquals(case, retryable, error.retryable)
                    assertEquals(case, 1, calls)
                    assertFallback(error)
                }
            }
        }

    @Test
    fun serverError_runRemote_preservesServerMessageAndCode() =
        runBlocking {
            val body = """{"isSuccess":"false","code":"DENIED","message":"권한 없음"}"""
            assertEquals(
                BaseState.Error("권한 없음", "DENIED", type = ErrorType.HTTP, httpStatus = 403),
                runRemote<String> { Response.error(403, body.toResponseBody("application/json".toMediaType())) },
            )
        }

    private fun assertFallback(error: BaseState.Error) {
        assertEquals("네트워크 통신 에러", error.msg)
        assertEquals("EMPTY", error.code)
        assertNotNull(error.cause)
    }

    @Test
    fun httpErrorBodyReadFails_runRemote_preservesHttpStatusAndRetryPolicy() =
        runBlocking {
            for ((status, retryable) in listOf(403 to false, 503 to true)) {
                for (cause in listOf(IOException("connection closed"), SocketTimeoutException("body timeout"))) {
                    val error = runRemote<String> { Response.error(status, unreadableBody(cause)) } as BaseState.Error
                    assertSame(cause, error.cause)
                    assertEquals(ErrorType.HTTP, error.type)
                    assertEquals(status, error.httpStatus)
                    assertEquals(retryable, error.retryable)
                    assertFallback(error)
                }
            }
        }

    @Test
    fun httpStatus_runRemote_classifiesRetryBoundary() =
        runBlocking {
            for ((status, retryable) in listOf(
                400 to false,
                401 to false,
                403 to false,
                404 to false,
                408 to true,
                409 to false,
                429 to true,
                499 to false,
                500 to true,
                503 to true,
                599 to true,
            )) {
                val body = """{"code":"SERVER_ERROR","message":"message"}"""
                val error = runRemote<String> {
                    Response.error(status, body.toResponseBody("application/json".toMediaType()))
                } as BaseState.Error

                assertEquals(ErrorType.HTTP, error.type)
                assertEquals(status, error.httpStatus)
                assertEquals("HTTP $status", retryable, error.retryable)
                assertEquals("message", error.msg)
                assertEquals("SERVER_ERROR", error.code)
            }
        }

    @Test
    fun transportAndParseFailure_runRemote_distinguishesRetryPolicy() =
        runBlocking {
            for ((cause, type) in listOf(
                SocketTimeoutException("timeout") to ErrorType.TIMEOUT,
                IOException("offline") to ErrorType.TRANSPORT,
                JsonSyntaxException("invalid body") to ErrorType.CONTRACT,
            )) {
                val error = runRemote<String> { throw cause } as BaseState.Error

                assertSame(cause, error.cause)
                assertEquals(type, error.type)
                assertEquals(type != ErrorType.CONTRACT, error.retryable)
                assertFallback(error)
            }
        }

    @Test
    fun cancelledErrorBodyRead_runRemote_propagatesOriginalCancellation() {
        val cancellation = CancellationException("cancelled while reading body")
        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking { runRemote<String> { Response.error(503, unreadableBody(cancellation)) } }
        }
        assertSame(cancellation, thrown)
    }

    private fun unreadableBody(cause: Exception): ResponseBody =
        object : ResponseBody() {
            override fun contentType() = "application/json".toMediaType()

            override fun contentLength() = 1L

            override fun source(): BufferedSource = throw cause
        }
}
