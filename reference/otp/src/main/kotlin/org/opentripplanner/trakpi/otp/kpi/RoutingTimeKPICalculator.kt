package org.opentripplanner.trakpi.otp.kpi

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.opentripplanner.trakpi.otp.tripObject
import org.opentripplanner.trakpi.tester.spi.KPICalculator
import org.opentripplanner.trakpi.tester.spi.Kpi
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/** OTP's server-side routing time from `debugOutput.totalTime` (nanoseconds), in milliseconds; null when absent. */
class RoutingTimeKPICalculator : KPICalculator {
    override fun calculate(response: TravelPlannerResponse): Kpi? {
        val nanos = response.tripObject()?.get("debugOutput")?.jsonObject?.get("totalTime")?.jsonPrimitive?.longOrNull ?: return null
        return Kpi("routingTimeMs", nanos / 1_000_000.0)
    }
}
