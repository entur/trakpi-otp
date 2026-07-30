package org.opentripplanner.trakpi.tester.spi.kpi

import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/** Computes one [Kpi] from a planner response, or null when the KPI does not apply to that response. */
interface KPICalculator {
    fun calculate(response: TravelPlannerResponse): Kpi?
}
