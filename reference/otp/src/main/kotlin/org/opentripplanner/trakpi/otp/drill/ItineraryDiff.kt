package org.opentripplanner.trakpi.otp.drill

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.opentripplanner.trakpi.otp.kpi.itineraryFingerprint

/**
 * Computes a single interleaved diff of two runs' itineraries for the same request.
 * Lines that are additional in [candidate] when compared to [reference], are prefixed with `+`,
 * and lines that have been removed in [candidate] with a `-` (these are in the [reference] but not in the [candidate]).
 * In case of a re-ordering, there will be both a `-` and a `+` entry.
 */
internal fun computeItineraryDiff(candidate: List<JsonObject>, reference: List<JsonObject>): List<String> {
    val candidateKeys = candidate.map { itineraryFingerprint(it) }
    val referenceKeys = reference.map { itineraryFingerprint(it) }
    return computeItineraryDiffLines(referenceKeys, candidateKeys).map { op ->
        when (op) {
            is Op.Equal -> "  ${renderItinerary(candidate[op.candidateIndex])}"
            is Op.Removed -> "- ${renderItinerary(reference[op.referenceIndex])}"
            is Op.Added -> "+ ${renderItinerary(candidate[op.candidateIndex])}"
        }
    }
}

/** A short "from -> to, first departs HH:MM" summary derived from a run's itineraries, or null when empty. */
internal fun searchSummary(patterns: List<JsonObject>): String? {
    val legs = patterns.firstOrNull()?.legs() ?: return null
    if (legs.isEmpty()) return null
    val from = legs.first().place("fromPlace")
    val to = legs.last().place("toPlace")
    return "$from  ->  $to   (first departs ${hhmm(legs.first().time("aimedStartTime"))})"
}

internal fun renderItinerary(pattern: JsonObject): String {
    val legs = pattern.legs()
    if (legs.isEmpty()) return "(empty)"
    val span = duration(legs)
    val body = legs.joinToString("  ->  ") { renderLeg(it) }
    return if (span != null) "($span) $body" else body
}

private fun renderLeg(leg: JsonObject): String {
    val from = leg.place("fromPlace")
    val to = leg.place("toPlace")
    val t1 = hhmm(leg.time("aimedStartTime"))
    val t2 = hhmm(leg.time("aimedEndTime"))
    if (leg.field("mode") == "foot") return "walk from $from ($t1) -> $to ($t2)"
    val label = leg.nested("line", "publicCode") ?: leg.field("mode") ?: "?"
    return "$label from $from ($t1) -> $to ($t2)"
}

private fun duration(legs: List<JsonObject>): String? {
    val start = legs.first().time("aimedStartTime") ?: return null
    val end = legs.last().time("aimedEndTime") ?: return null
    val minutes = ((epochOrNull(end) ?: return null) - (epochOrNull(start) ?: return null)) / 60
    if (minutes < 0) return null
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

// --- JSON helpers -------------------------------------------------------------------------------------

private fun JsonObject.legs(): List<JsonObject> = (this["legs"] as? JsonArray).orEmpty().map { it.jsonObject }

private fun JsonObject.field(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.nested(obj: String, key: String): String? =
    (this[obj] as? JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull

private fun JsonObject.time(key: String): String? = field(key)

private fun JsonObject.place(obj: String): String = nested(obj, "name") ?: "?"

/** ISO-8601 like "2026-09-01T08:00:00+02:00" -> "08:00"; "--:--" when absent or too short. */
private fun hhmm(iso: String?): String = if (iso != null && iso.length >= 16) iso.substring(11, 16) else "--:--"

/** Epoch seconds from an offset date-time string, or null when unparseable. */
private fun epochOrNull(iso: String): Long? =
    runCatching { java.time.OffsetDateTime.parse(iso).toEpochSecond() }.getOrNull()

// --- Order-preserving diff (LCS) ----------------------------------------------------------------------

private sealed interface Op {
    data class Equal(val referenceIndex: Int, val candidateIndex: Int) : Op

    data class Removed(val referenceIndex: Int) : Op

    data class Added(val candidateIndex: Int) : Op
}

/**
 * Computes each line of the diff comparing the [candidate] to the [reference].
 * Shared items are `Equal`, items only in [reference] are `Removed`, items only in [candidate] are `Added`.
 *
 * Computation is done using a longest-common-subsequence alignment of [reference] and [candidate], as an ordered op list.
 */
private fun computeItineraryDiffLines(reference: List<List<String>>, candidate: List<List<String>>): List<Op> {
    val n = reference.size
    val m = candidate.size
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) {
        for (j in m - 1 downTo 0) {
            dp[i][j] = if (reference[i] == candidate[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
    }
    val ops = mutableListOf<Op>()
    var i = 0
    var j = 0
    while (i < n && j < m) {
        when {
            reference[i] == candidate[j] -> {
                ops.add(Op.Equal(i, j)); i++; j++
            }
            dp[i + 1][j] >= dp[i][j + 1] -> {
                ops.add(Op.Removed(i)); i++
            }
            else -> {
                ops.add(Op.Added(j)); j++
            }
        }
    }
    while (i < n) ops.add(Op.Removed(i++))
    while (j < m) ops.add(Op.Added(j++))
    return ops
}
