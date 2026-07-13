package org.opentripplanner.trakpi.otp.kpi

import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.opentripplanner.trakpi.otp.tripPatterns
import org.opentripplanner.trakpi.tester.spi.KPICalculator
import org.opentripplanner.trakpi.tester.spi.Kpi
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/** Duration of the fastest itinerary in seconds; null when the response has no itineraries. */
class FastestItineraryKPICalculator : KPICalculator {
    override fun calculate(response: TravelPlannerResponse): Kpi? {
        val fastest = response.tripPatterns().mapNotNull { it["duration"]?.jsonPrimitive?.longOrNull }.minOrNull() ?: return null
        return Kpi("fastestDurationSeconds", fastest.toDouble())
    }
}
