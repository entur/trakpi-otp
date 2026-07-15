package org.opentripplanner.trakpi.tester.spi

/**
 * A planner's response to a request. The [raw] body is opaque to trakpi. Every planner reports two
 * generic facts: whether the request was a [success], and the [method] it invoked
 * (e.g. "plan", or "departures"). [attributes] carry implementation-specific dimensions, such as
 * HTTP status code for planners using HTTP.
 */
data class TravelPlannerResponse(
    val raw: String,
    val success: Boolean,
    val method: String,
    val attributes: Map<String, String> = emptyMap(),
)
