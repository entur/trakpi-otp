package org.opentripplanner.trakpi.otp.kpi

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.opentripplanner.trakpi.otp.tripPatterns
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse
import org.opentripplanner.trakpi.tester.spi.kpi.Kpi

/**
 * Shortest street distance (walking, cycling and other non-transit legs) in meters across the itineraries that use
 * transit. Null when no itinerary uses transit, or when a leg lacks `ride`.
 */
class MinStreetDistanceKPICalculator : OtpKPICalculator {
    override val requiredFields = RequiredFields(setOf("trip"), "{ tripPatterns { streetDistance legs { ride } } }")

    override fun calculate(response: TravelPlannerResponse): Kpi? {
        val distance =
            response.tripPatterns()
                .filter { tp ->
                    val legs = tp["legs"] as? JsonArray ?: JsonArray(emptyList())
                    legs.any { it.jsonObject["ride"]?.jsonPrimitive?.booleanOrNull ?: return null }
                }
                .mapNotNull { it["streetDistance"]?.jsonPrimitive?.doubleOrNull }
                .minOrNull() ?: return null
        return Kpi("minStreetDistanceMeters", distance)
    }
}
