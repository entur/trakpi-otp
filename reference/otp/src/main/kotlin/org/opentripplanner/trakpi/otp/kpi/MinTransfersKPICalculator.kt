package org.opentripplanner.trakpi.otp.kpi

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.opentripplanner.trakpi.otp.tripPatterns
import org.opentripplanner.trakpi.tester.spi.kpi.Kpi
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/**
 * Fewest transfers across the returned itineraries (transit legs minus one). A leg is transit when OTP marks it
 * as a `ride`, so bicycle, car and scooter legs do not count. Null when the response has no itineraries, or when
 * a leg lacks `ride`.
 */
class MinTransfersKPICalculator : OtpKPICalculator {
    override val requiredFields = RequiredFields(setOf("trip"), "{ tripPatterns { legs { ride } } }")

    override fun calculate(response: TravelPlannerResponse): Kpi? {
        val transfers =
            response.tripPatterns().map { tp ->
                val legs = tp["legs"] as? JsonArray ?: JsonArray(emptyList())
                val transitLegs = legs.count { it.jsonObject["ride"]?.jsonPrimitive?.booleanOrNull ?: return null }
                (transitLegs - 1).coerceAtLeast(0)
            }.minOrNull() ?: return null
        return Kpi("minTransfers", transfers.toDouble())
    }
}
