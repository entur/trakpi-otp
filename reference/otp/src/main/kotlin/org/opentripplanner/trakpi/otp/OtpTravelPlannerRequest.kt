package org.opentripplanner.trakpi.otp

import kotlinx.serialization.json.JsonElement
import org.opentripplanner.trakpi.tester.spi.TravelPlannerRequest

/**
 * An OTP request: a GraphQL [query] to POST to the Transmodel API, with optional GraphQL [variables].
 */
data class OtpTravelPlannerRequest(val query: String, val variables: JsonElement? = null) : TravelPlannerRequest
