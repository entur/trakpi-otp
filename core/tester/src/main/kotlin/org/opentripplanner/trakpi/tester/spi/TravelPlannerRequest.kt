package org.opentripplanner.trakpi.tester.spi

/**
 * A request to a travel planner: the testset request identified by [id]. The [raw] body is opaque to
 * trakpi. The planner interprets it when executing the request, and KPI calculators may interpret it too.
 */
data class TravelPlannerRequest(val id: String, val raw: String)
