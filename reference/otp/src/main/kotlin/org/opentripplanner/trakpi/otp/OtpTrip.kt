package org.opentripplanner.trakpi.otp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/**
 * Reads the OTP `trip` object out of a raw response body; null when the body is not JSON or carries no
 * `trip` object (e.g. `"trip": null` after a resolver error), so a failed response yields no KPI rather than
 * throwing.
 */
internal fun TravelPlannerResponse.tripObject(): JsonObject? {
    val json = runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return null
    return (json["data"] as? JsonObject)?.get("trip") as? JsonObject
}

/** The trip patterns (itineraries) of an OTP `trip` response, empty when there are none. */
internal fun TravelPlannerResponse.tripPatterns(): List<JsonObject> =
    (tripObject()?.get("tripPatterns") as? JsonArray)?.map { it.jsonObject } ?: emptyList()
