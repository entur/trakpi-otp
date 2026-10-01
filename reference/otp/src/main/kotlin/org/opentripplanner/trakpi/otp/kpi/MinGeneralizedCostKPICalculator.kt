package org.opentripplanner.trakpi.otp.kpi

import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.opentripplanner.trakpi.otp.tripPatterns
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse
import org.opentripplanner.trakpi.tester.spi.kpi.Kpi

/**
 * Lowest generalized cost across the returned itineraries.
 * Null when the response has no itineraries with a cost.
 */
class MinGeneralizedCostKPICalculator : OtpKPICalculator {
    override val requiredFields = RequiredFields(setOf("trip"), "{ tripPatterns { generalizedCost } }")

    override fun calculate(response: TravelPlannerResponse): Kpi? {
        val cost = response.tripPatterns().mapNotNull { it["generalizedCost"]?.jsonPrimitive?.longOrNull }.minOrNull() ?: return null
        return Kpi("minGeneralizedCost", cost.toDouble())
    }
}
