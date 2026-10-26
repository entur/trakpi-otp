package org.opentripplanner.trakpi.tester.spi.kpi

import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/** A [KPICalculator] that reads only the response. */
fun interface ResponseKPICalculator : KPICalculator {
    /** Computes the [Kpi] from [response], or null when the KPI does not apply to that response. */
    fun calculate(response: TravelPlannerResponse): Kpi?
}
