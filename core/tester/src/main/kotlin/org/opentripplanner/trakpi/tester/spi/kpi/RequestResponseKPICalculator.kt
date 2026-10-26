package org.opentripplanner.trakpi.tester.spi.kpi

import org.opentripplanner.trakpi.tester.spi.TravelPlannerRequest
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/** A [KPICalculator] that reads the request as well as the response. */
fun interface RequestResponseKPICalculator : KPICalculator {
    /** Computes the [Kpi] from [request] and its [response], or null when the KPI does not apply to them. */
    fun calculate(request: TravelPlannerRequest, response: TravelPlannerResponse): Kpi?
}
