package org.opentripplanner.trakpi.otp

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Decides whether an OTP instance at a given endpoint is up and serving. Independent of how the instance
 * was launched, so any [OtpCluster] implementation can reuse it rather than reimplementing the check.
 */
fun interface OtpReadinessProbe {
    fun responds(endpoint: String): Boolean
}

/** Probes readiness by asking OTP for its `serverInfo`; treats any transport error as "not ready yet". */
class ServerInfoProbe(private val clientName: String) : OtpReadinessProbe {
    // HTTP/1.1 explicitly: the default HTTP/2 client's h2c upgrade over cleartext http can truncate a
    // POST body against OTP's Grizzly servers, resulting in an EOFException.
    private val http =
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build()

    override fun responds(endpoint: String): Boolean =
        try {
            val request =
                HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("ET-Client-Name", clientName)
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString("""{"query":"{ serverInfo { version } }"}"""))
                    .build()
            val response = http.send(request, BodyHandlers.ofString())
            val data = Json.parseToJsonElement(response.body()).jsonObject["data"] as? JsonObject
            response.statusCode() / 100 == 2 && data?.get("serverInfo") != null
        } catch (e: Exception) {
            false // connection refused, still building or otherwise not ready yet
        }
}
