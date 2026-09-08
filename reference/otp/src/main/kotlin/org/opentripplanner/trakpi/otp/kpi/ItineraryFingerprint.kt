package org.opentripplanner.trakpi.otp.kpi

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Fingerprints an itinerary (a `trip` pattern) by its legs, so two itineraries are "the same" iff their leg
 * fingerprints match in order. Shared by [ItinerariesMatchReferenceKPICalculator], which scores on it, and
 * the drill-down renderer, which diffs on it, so the drill-down agrees with the KPI verdict.
 *
 * A leg is identified by mode, service journey and line, from- and to-place, and the scheduled (aimed) times.
 * Aimed times are used rather than expected so a fingerprint is unaffected by real-time delays that differ
 * between two runs executed at different moments.
 */
internal fun itineraryFingerprint(pattern: JsonObject): List<String> =
    (pattern["legs"] as? JsonArray).orEmpty().map { legFingerprint(it.jsonObject) }

internal fun legFingerprint(leg: JsonObject): String {
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
