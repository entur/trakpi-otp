package org.opentripplanner.trakpi.otp.kpi

import org.opentripplanner.trakpi.otp.tripObject
import org.opentripplanner.trakpi.otp.tripPatterns
import org.opentripplanner.trakpi.tester.spi.kpi.Kpi
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/** Number of itineraries (trip patterns) in an OTP `trip` response; null for non-routing responses (no `trip`). */
class ItineraryCountKPICalculator : OtpKPICalculator {
    // Only counting patterns in the tripPatterns collection matters, so `__typename` is the minimal needed selection.
    override val requiredFields = RequiredFields(setOf("trip"), "{ tripPatterns { __typename } }")

    override fun calculate(response: TravelPlannerResponse): Kpi? {
        response.tripObject() ?: return null
        return Kpi("itineraryCount", response.tripPatterns().size.toDouble())
    }
}
