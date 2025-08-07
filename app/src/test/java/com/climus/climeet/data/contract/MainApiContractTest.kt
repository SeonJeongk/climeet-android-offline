package com.climus.climeet.data.contract

import com.climus.climeet.data.model.request.CreateClimbingRecordRequest
import com.climus.climeet.data.model.request.GetGymRouteInfoRequest
import com.climus.climeet.data.model.response.GetGymFilteringKeyResponse
import com.climus.climeet.data.model.response.GetGymRouteInfoResponse
import com.climus.climeet.data.remote.MainApi
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.Base64

class MainApiContractTest {
    private val gson = Gson()

    @Test
    fun filteringKeyFixture_deserialize_matchesResponseContract() {
        val json = fixture("get-gym-filtering-key-response.json")
        val response = gson.fromJson(json, GetGymFilteringKeyResponse::class.java)

        assertEquals(1, response.gymId)
        assertEquals(2, response.maxFloor)
        assertEquals(
            listOf("VB", "V1", "V2", "V3", "V4", "V5", "V6", "V7", "V8", "V9", "C1", "C2", "C3"),
            response.difficultyList.map { it.gymDifficultyName },
        )
        assertEquals(
            listOf(
                "#FFFFFF",
                "#F34040",
                "#FF9000",
                "#FDDA16",
                "#63B75D",
                "#0094FF",
                "#393FD6",
                "#A259FF",
                "#6E4C41",
                "#8B8B8B",
                "#000000",
                "#000000",
                "#000000",
            ),
            response.difficultyList.map { it.gymDifficultyColor },
        )
        assertEquals(4, response.sectorList.size)
        assertEquals("VB", response.difficultyList.first().climeetDifficultyName)
        assertEquals(0, response.difficultyList.first().difficulty)
        assertEquals("2026-08-12", response.timePoint)
        assertJsonRoundTrip(json, response)
    }

    @Test
    fun minimalRouteFixture_deserialize_preservesNullFilters() {
        val json = fixture("get-gym-route-info-request-minimal.json")
        val request = gson.fromJson(json, GetGymRouteInfoRequest::class.java)

        assertEquals(0, request.page)
        assertEquals(10, request.size)
        assertNull(request.floor)
        assertNull(request.sectorId)
        assertNull(request.difficulty)
        assertNull(request.timePoint)

        val encoded = JsonParser().parse(gson.toJson(request)).asJsonObject
        assertFalse(encoded.has("floor"))
        assertFalse(encoded.has("sectorId"))
        assertFalse(encoded.has("difficulty"))
        assertFalse(encoded.has("timePoint"))
    }

    @Test
    fun filteredRouteFixture_deserialize_preservesFields() {
        val json = fixture("get-gym-route-info-request-filtered.json")
        val request = gson.fromJson(json, GetGymRouteInfoRequest::class.java)

        assertEquals(1, request.page)
        assertEquals(10, request.size)
        assertEquals(2, request.floor)
        assertEquals(201L, request.sectorId)
        assertEquals(2, request.difficulty)
        assertEquals("2026-08-12", request.timePoint)
        assertJsonRoundTrip(json, request)
    }

    @Test
    fun routeResponseFixture_deserialize_matchesResponseContract() {
        val json = fixture("get-gym-route-info-response.json")
        val response = gson.fromJson(json, GetGymRouteInfoResponse::class.java)

        assertEquals(0, response.page)
        assertTrue(response.hasNext)
        assertEquals(listOf(1001L, 1002L, 1003L), response.result.map { it.routeId })
        assertEquals("VB", response.result.first().gymDifficultyName)
        assertEquals("VB", response.result.first().climeetDifficultyName)
        assertEquals(0, response.result.first().difficulty)
        assertJsonRoundTrip(json, response)
    }

    @Test
    fun recordRequestFixture_roundTrip_preservesRequestBody() {
        val json = fixture("create-climbing-record-request.json")
        val request = gson.fromJson(json, CreateClimbingRecordRequest::class.java)

        assertEquals(1L, request.gymId)
        assertEquals("2026-08-12", request.date)
        assertEquals("01:23:45", request.time)
        assertEquals(1, request.avgDifficulty)
        assertEquals(2, request.routeRecordRequestDtoList.size)
        assertJsonRoundTrip(json, request)
    }

    @Test
    fun recordSuccessFixture_readContract_returns200WithEmptyBody() {
        val contract = JsonParser()
            .parse(fixture("create-climbing-record-success.json"))
            .asJsonObject

        assertEquals(200, contract["statusCode"].asInt)
        assertEquals(0, Base64.getDecoder().decode(contract["bodyBase64"].asString).size)
    }

    @Test
    fun mainApiMethods_inspectAnnotations_preservesWireContract() {
        val filteringKey = MainApi::class.java.getDeclaredMethod(
            "getGymFilteringKey",
            Long::class.javaPrimitiveType,
            kotlin.coroutines.Continuation::class.java,
        )
        assertEquals(
            "/api/gyms/{gymId}/version/key",
            requireNotNull(filteringKey.getAnnotation(GET::class.java)).value,
        )
        assertParameterAnnotation(filteringKey, 0, Path::class.java, "gymId")

        val filteringKeyTime = MainApi::class.java.getDeclaredMethod(
            "getGymFilteringKeyTime",
            Long::class.javaPrimitiveType,
            String::class.java,
            kotlin.coroutines.Continuation::class.java,
        )
        assertEquals(
            "/api/gyms/{gymId}/version/key",
            requireNotNull(filteringKeyTime.getAnnotation(GET::class.java)).value,
        )
        assertParameterAnnotation(filteringKeyTime, 0, Path::class.java, "gymId")
        assertParameterAnnotation(filteringKeyTime, 1, Query::class.java, "timePoint")

        val route = MainApi::class.java.getDeclaredMethod(
            "getGymRouteInfoList",
            Long::class.javaPrimitiveType,
            GetGymRouteInfoRequest::class.java,
            kotlin.coroutines.Continuation::class.java,
        )
        assertEquals(
            "/api/gyms/{gymId}/version/route",
            requireNotNull(route.getAnnotation(POST::class.java)).value,
        )
        assertParameterAnnotation(route, 0, Path::class.java, "gymId")
        assertTrue(route.parameterAnnotations[1].any { it is Body })

        val record = MainApi::class.java.getDeclaredMethod(
            "createClimbingRecord",
            CreateClimbingRecordRequest::class.java,
            kotlin.coroutines.Continuation::class.java,
        )
        assertEquals(
            "/api/climbing-records",
            requireNotNull(record.getAnnotation(POST::class.java)).value,
        )
        assertTrue(record.parameterAnnotations[0].any { it is Body })
        assertTrue(record.genericParameterTypes[0].typeName.endsWith("CreateClimbingRecordRequest"))
    }

    private fun fixture(name: String): String = fixtureBytes(name).toString(Charsets.UTF_8)

    private fun fixtureBytes(name: String): ByteArray =
        checkNotNull(javaClass.classLoader?.getResource("contracts/$name")) {
            "Missing contract fixture: $name"
        }.readBytes()

    private fun assertJsonRoundTrip(
        expectedJson: String,
        value: Any,
    ) {
        assertEquals(
            JsonParser().parse(expectedJson),
            JsonParser().parse(gson.toJson(value)),
        )
    }

    private fun <T : Annotation> assertParameterAnnotation(
        method: java.lang.reflect.Method,
        parameterIndex: Int,
        annotationType: Class<T>,
        expectedValue: String,
    ) {
        val annotation = method.parameterAnnotations[parameterIndex]
            .first { annotationType.isInstance(it) }
        val value = annotationType.getMethod("value").invoke(annotation)
        assertEquals(expectedValue, value)
    }
}
