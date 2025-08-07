package com.climus.climeet.data.contract

import com.climus.climeet.data.model.request.CreateClimbingRecordRequest
import com.climus.climeet.data.model.request.GetGymRouteInfoRequest
import com.climus.climeet.data.remote.MainApi
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainApiFixtureIntegrationTest {
    private val gson = Gson()

    @Test
    fun publishedFixtures_callMainApi_preservesRouteAndRecordContract() =
        runBlocking {
            MockWebServer().use { server ->
                server.enqueue(jsonResponse(fixture("get-gym-filtering-key-response.json")))
                server.enqueue(jsonResponse(fixture("get-gym-route-info-response.json")))
                server.enqueue(MockResponse().setResponseCode(200).setBody(""))
                server.start()

                val api = Retrofit.Builder()
                    .baseUrl(server.url("/"))
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(MainApi::class.java)

                val keyResponse = api.getGymFilteringKey(1L)
                assertEquals(200, keyResponse.code())
                val key = checkNotNull(keyResponse.body())
                assertEquals(2, key.maxFloor)
                assertEquals(4, key.sectorList.size)

                val routeRequest = GetGymRouteInfoRequest(
                    page = 1,
                    size = 10,
                    floor = 2,
                    sectorId = 201L,
                    difficulty = 2,
                    timePoint = "2026-08-12",
                )
                val routesResponse = api.getGymRouteInfoList(1L, routeRequest)
                assertEquals(200, routesResponse.code())
                val routes = checkNotNull(routesResponse.body())
                assertFalse(routes.result.map { it.routeId }.hasDuplicates())

                val recordRequest = gson.fromJson(
                    fixture("create-climbing-record-request.json"),
                    CreateClimbingRecordRequest::class.java,
                )
                val recordResponse = api.createClimbingRecord(recordRequest)
                assertEquals(200, recordResponse.code())
                assertNotNull(recordResponse.body())
                assertEquals(0L, recordResponse.body()!!.contentLength())

                val keyHttpRequest = server.takeRequest()
                assertEquals("GET", keyHttpRequest.method)
                assertEquals("/api/gyms/1/version/key", keyHttpRequest.path)

                val routeHttpRequest = server.takeRequest()
                assertEquals("POST", routeHttpRequest.method)
                assertEquals("/api/gyms/1/version/route", routeHttpRequest.path)
                assertEquals(
                    JsonParser().parse(fixture("get-gym-route-info-request-filtered.json")),
                    JsonParser().parse(routeHttpRequest.body.readUtf8()),
                )

                val recordHttpRequest = server.takeRequest()
                assertEquals("POST", recordHttpRequest.method)
                assertEquals("/api/climbing-records", recordHttpRequest.path)
                assertEquals(
                    JsonParser().parse(fixture("create-climbing-record-request.json")),
                    JsonParser().parse(recordHttpRequest.body.readUtf8()),
                )
            }
        }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource("contracts/$name")) {
            "Missing contract fixture: $name"
        }.readText()

    private fun <T> List<T>.hasDuplicates(): Boolean = size != toSet().size
}
