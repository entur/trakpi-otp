package org.opentripplanner.trakpi.otp.drill

import org.opentripplanner.trakpi.otp.tripPatterns
import org.opentripplanner.trakpi.tester.spi.DrillDownRenderer
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/**
 * The OTP [DrillDownRenderer]: renders a run's `trip` response against its reference as an interleaved itinerary
 * diff (see [computeItineraryDiff]). With no reference, it lists the candidate's itineraries on their own.
 * Non-`trip` responses have no itineraries and render as an empty diff.
 */
class OtpDrillDownRenderer : DrillDownRenderer {
    override fun render(candidate: TravelPlannerResponse, reference: TravelPlannerResponse?): List<String> {
        val candidatePatterns = candidate.tripPatterns()
        val lines = mutableListOf<String>()
        searchSummary(candidatePatterns)?.let { lines.add(it) }
        if (reference == null) {
            lines.add("No reference response to diff against; candidate itineraries:")
            candidatePatterns.forEach { lines.add("  ${renderItinerary(it)}") }
        } else {
            lines.add("(+ only in candidate, - only in reference)")
            lines.add("")
            lines.addAll(computeItineraryDiff(candidatePatterns, reference.tripPatterns()))
        }
        return lines
    }
}
