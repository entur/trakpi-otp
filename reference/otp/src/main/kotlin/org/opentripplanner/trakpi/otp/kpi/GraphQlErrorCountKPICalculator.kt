package org.opentripplanner.trakpi.otp.kpi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse
import org.opentripplanner.trakpi.tester.spi.kpi.Kpi

/**
 * Number of entries in the top-level GraphQL `errors` array, 0 when it is absent.
 * Null when the body is not a JSON object, e.g. an HTML error page from a proxy.
 */
class GraphQlErrorCountKPICalculator : OtpKPICalculator {
    override fun calculate(response: TravelPlannerResponse): Kpi? {
        val json = runCatching { Json.parseToJsonElement(response.raw) }.getOrNull() as? JsonObject ?: return null
        val errors = json["errors"] as? JsonArray
        return Kpi("graphqlErrorCount", (errors?.size ?: 0).toDouble())
    }
}
