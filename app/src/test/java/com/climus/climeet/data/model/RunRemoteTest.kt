package com.climus.climeet.data.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Response

class RunRemoteTest {
    @Test
    fun successfulRequest_runRemote_invokesOnceAndPreservesBodyIdentity() =
        runBlocking {
            var calls = 0
            val body = listOf("response")
            val result = runRemote {
                calls++
                Response.success(body)
            } as BaseState.Success
            assertEquals(1, calls)
            assertSame(body, result.body)
        }

    @Test
    fun successfulResponse_runRemote_preservesBody() =
        runBlocking {
            assertEquals(BaseState.Success("response"), runRemote { Response.success("response") })
        }

    @Test
    fun emptyResponse_runRemote_preservesEmptyError() =
        runBlocking {
            assertEquals(
                BaseState.Error("응답이 비어있습니다", "EMPTY"),
                runRemote { Response.success<String>(null) },
            )
        }

    @Test
    fun cancelledRequest_runRemote_propagatesOriginalCancellation() {
        val cancellation = CancellationException("cancelled")
        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking { runRemote<String> { throw cancellation } }
        }
        assertSame(cancellation, thrown)
    }
}
