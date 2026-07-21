package org.opentripplanner.trakpi.otp.testset.transforms

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/**
 * A [CoordinateSnapper] backed by every transit station OTP knows. The full station list is fetched from
 * [endpoint] once, on first use, and indexed in memory, so only a run that actually obfuscates
 * coordinates has to fetch, and each subsequent coordinate is then snapped without a network call.
 * Authenticates with the `ET-Client-Name` header, like the travel planner.
 */
class OtpStationSnapper(private val endpoint: String, private val clientName: String) : CoordinateSnapper {
    private val index: StationIndex by lazy { StationIndex(fetchStations()) }

    override fun snap(coordinate: Coordinate): Coordinate = index.snap(coordinate)

    private fun fetchStations(): List<Coordinate> {
        println("Fetching transit stations from $endpoint ...")
        val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        val request =
            HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("ET-Client-Name", clientName)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(QUERY))
                .build()
        val body = http.send(request, BodyHandlers.ofString()).body()
        val stopPlaces =
            Json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("stopPlaces") as? JsonArray
                ?: error("No stopPlaces in response from $endpoint")
        val stations =
            stopPlaces.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val latitude = (obj["latitude"] as? JsonPrimitive)?.doubleOrNull
                val longitude = (obj["longitude"] as? JsonPrimitive)?.doubleOrNull
                if (latitude != null && longitude != null) Coordinate(latitude, longitude) else null
            }
        println("Indexed ${stations.size} stations.")
        return stations
    }

    private companion object {
        const val QUERY = """{"query":"{ stopPlaces { latitude longitude } }"}"""
    }
}
