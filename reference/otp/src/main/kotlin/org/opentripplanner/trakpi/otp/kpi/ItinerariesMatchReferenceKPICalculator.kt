package org.opentripplanner.trakpi.otp.kpi

import org.opentripplanner.trakpi.otp.tripObject
import org.opentripplanner.trakpi.otp.tripPatterns
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse
import org.opentripplanner.trakpi.tester.spi.kpi.Kpi

/**
 * 1.0 when the subject response returns the same itineraries as the reference, in the same order, as the reference
 * response, and 0.0 when any of the itineraries differ. Null when either response is not a `trip` routing response, so
 * the KPI is emitted only where the comparison is meaningful.
 *
 * Each itinerary is fingerprinted by its legs, so that is mode, service journey (or line), from- and to-place, and the
 * scheduled (aimed) times. Aimed times are used rather than expected so this KPI is unaffected by real-time delays.
 */
class ItinerariesMatchReferenceKPICalculator : OtpComparativeKPICalculator {
    override val requiredFields =
        RequiredFields(
            setOf("trip"),
            "{ tripPatterns { legs { mode aimedStartTime aimedEndTime " +
                "fromPlace { name } toPlace { name } line { publicCode } serviceJourney { id } } } }",
        )

    override fun calculate(subject: TravelPlannerResponse, reference: TravelPlannerResponse): Kpi? {
        subject.tripObject() ?: return null
        reference.tripObject() ?: return null
        val matches = subject.tripPatterns().map(::itineraryFingerprint) == reference.tripPatterns().map(::itineraryFingerprint)
        return Kpi("itinerariesMatchReference", if (matches) 1.0 else 0.0)
    }
}
