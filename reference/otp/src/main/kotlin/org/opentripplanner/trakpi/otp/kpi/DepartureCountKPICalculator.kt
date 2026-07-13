package org.opentripplanner.trakpi.otp.kpi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.opentripplanner.trakpi.tester.spi.KPICalculator
import org.opentripplanner.trakpi.tester.spi.Kpi
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/** Number of departures (estimatedCalls) on a stopPlace or quay response; null when neither is present. */
class DepartureCountKPICalculator : KPICalculator {
    override fun calculate(response: TravelPlannerResponse): Kpi? {
        val data = (Json.parseToJsonElement(response.raw) as? JsonObject)?.get("data") as? JsonObject ?: return null
        val place = data["stopPlace"] as? JsonObject ?: data["quay"] as? JsonObject ?: return null
        val calls = place["estimatedCalls"] as? JsonArray ?: return null
        return Kpi("departureCount", calls.size.toDouble())
    }
}
