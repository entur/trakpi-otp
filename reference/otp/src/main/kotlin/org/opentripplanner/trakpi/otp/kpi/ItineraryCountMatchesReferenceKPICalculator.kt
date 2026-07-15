package org.opentripplanner.trakpi.otp.kpi

import org.opentripplanner.trakpi.otp.tripObject
import org.opentripplanner.trakpi.otp.tripPatterns
import org.opentripplanner.trakpi.tester.spi.ComparativeKPICalculator
import org.opentripplanner.trakpi.tester.spi.Kpi
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/**
 * A crude regression signal: 1.0 when the subject response returned the same number of itineraries
 * (trip patterns) as the reference response, 0.0 when it differs. Null when either response is not a
 * `trip` routing response, so the KPI is emitted only where the comparison is meaningful.
 */
class ItineraryCountMatchesReferenceKPICalculator : ComparativeKPICalculator {
    override fun calculate(subject: TravelPlannerResponse, reference: TravelPlannerResponse): Kpi? {
        subject.tripObject() ?: return null
        reference.tripObject() ?: return null
        val matches = subject.tripPatterns().size == reference.tripPatterns().size
        return Kpi("itineraryCountMatchesReference", if (matches) 1.0 else 0.0)
    }
}
