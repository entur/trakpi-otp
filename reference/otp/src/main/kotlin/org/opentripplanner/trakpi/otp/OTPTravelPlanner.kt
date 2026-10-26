package org.opentripplanner.trakpi.otp

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.opentripplanner.trakpi.tester.spi.TravelPlanner
import org.opentripplanner.trakpi.tester.spi.TravelPlannerRequest
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/** Executes a request by POSTing its GraphQL query to an OTP Transmodel endpoint. */
class OTPTravelPlanner(
    private val endpoint: String,
    private val clientName: String,
) : TravelPlanner {
    // HTTP/1.1 explicitly: the default HTTP/2 client's h2c upgrade over cleartext http can truncate a
    // POST body against OTP's Grizzly servers, resulting in an EOFException.
    private val http =
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build()

    override fun execute(request: TravelPlannerRequest): TravelPlannerResponse {
        val graphQl = OtpRequestBody.parse(request.raw)
        val body =
            buildJsonObject {
                    put("query", JsonPrimitive(graphQl.query))
                    graphQl.variables?.let { put("variables", it) }
                }
                .toString()
        val httpRequest =
            HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("ET-Client-Name", clientName)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        val start = System.nanoTime()
        val response = http.send(httpRequest, BodyHandlers.ofString())
        val responseTime = Duration.ofNanos(System.nanoTime() - start)
        return toResponse(response.body(), response.statusCode(), responseTime)
    }

    /**
     * Derives the generic response facts from a GraphQL reply. The method is the queried root
     * field (e.g. trip or stopPlace), and the request succeeded when the transport returned 2xx
     * and the body carries `data` with no `errors`.
     */
    private fun toResponse(body: String, statusCode: Int, responseTime: Duration): TravelPlannerResponse {
        val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
        val data = json?.get("data") as? JsonObject
        val errors = json?.get("errors") as? JsonArray
        val success = statusCode / 100 == 2 && data != null && errors.isNullOrEmpty()
        return TravelPlannerResponse(
            raw = body,
            success = success,
            method = data?.keys?.firstOrNull() ?: "unknown",
            attributes =
                mapOf(
                    "http_status_code" to statusCode.toString(),
                    "http_status_class" to HttpStatusClass.of(statusCode).label,
                ),
            responseTime = responseTime,
        )
    }
}
