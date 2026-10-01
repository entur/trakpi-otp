package org.opentripplanner.trakpi.otp.kpi

import java.time.Duration
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.opentripplanner.trakpi.otp.tripPatterns
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse
import org.opentripplanner.trakpi.tester.spi.kpi.Kpi

/**
 * Shortest transfer in seconds across the returned itineraries: the time from alighting one transit (`ride`) leg to
 * boarding the next, so any walk and wait in between. Only scheduled (aimed) times are used, so real-time delays do
 * not affect the value. Null when no itinerary has a transfer, or when a leg lacks `ride` or parseable aimed times.
 */
class MinTransferTimeKPICalculator : OtpKPICalculator {
    override val requiredFields = RequiredFields(setOf("trip"), "{ tripPatterns { legs { ride aimedStartTime aimedEndTime } } }")

    override fun calculate(response: TravelPlannerResponse): Kpi? {
        val transferTimes =
            response.tripPatterns().flatMap { tp ->
                val legs = (tp["legs"] as? JsonArray).orEmpty().map { it.jsonObject }
                val rides = legs.filter { it["ride"]?.jsonPrimitive?.booleanOrNull ?: return null }
                rides.zipWithNext { alight, board ->
                    val alightAt = alight.time("aimedEndTime") ?: return null
                    val boardAt = board.time("aimedStartTime") ?: return null
                    Duration.between(alightAt, boardAt).seconds
                }
            }
        val shortest = transferTimes.minOrNull() ?: return null
        return Kpi("minTransferTimeSeconds", shortest.toDouble())
    }

    private fun JsonObject.time(key: String): OffsetDateTime? =
        this[key]?.jsonPrimitive?.contentOrNull?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
}
