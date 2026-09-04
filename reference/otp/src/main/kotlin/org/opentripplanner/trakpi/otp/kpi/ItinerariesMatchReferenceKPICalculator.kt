package org.opentripplanner.trakpi.otp.kpi

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        val matches = subject.tripPatterns().map(::fingerprint) == reference.tripPatterns().map(::fingerprint)
        return Kpi("itinerariesMatchReference", if (matches) 1.0 else 0.0)
    }

    /** An itinerary's identity: the ordered fingerprints of its legs. */
    private fun fingerprint(pattern: JsonObject): List<String> =
        (pattern["legs"] as? JsonArray).orEmpty().map { leg -> legFingerprint(leg.jsonObject) }

    private fun legFingerprint(leg: JsonObject): String {
        fun field(key: String) = leg[key]?.jsonPrimitive?.contentOrNull ?: ""
        fun nested(obj: String, key: String) = (leg[obj] as? JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull ?: ""
        return listOf(
                field("mode"),
                nested("serviceJourney", "id"),
                nested("line", "publicCode"),
                nested("fromPlace", "name"),
                nested("toPlace", "name"),
                field("aimedStartTime"),
                field("aimedEndTime"),
            )
            .joinToString("|")
    }
}
