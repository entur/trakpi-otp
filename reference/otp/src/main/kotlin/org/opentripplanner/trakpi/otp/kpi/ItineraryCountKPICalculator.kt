package org.opentripplanner.trakpi.otp.kpi

import org.opentripplanner.trakpi.otp.tripObject
import org.opentripplanner.trakpi.otp.tripPatterns
import org.opentripplanner.trakpi.tester.spi.KPICalculator
import org.opentripplanner.trakpi.tester.spi.Kpi
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/** Number of itineraries (trip patterns) in an OTP `trip` response; null for non-routing responses (no `trip`). */
class ItineraryCountKPICalculator : KPICalculator {
    override fun calculate(response: TravelPlannerResponse): Kpi? {
        response.tripObject() ?: return null // not a routing response — itinerary count doesn't apply
        return Kpi("itineraryCount", response.tripPatterns().size.toDouble())
    }
}
