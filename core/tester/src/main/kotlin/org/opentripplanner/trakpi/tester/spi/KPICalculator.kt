package org.opentripplanner.trakpi.tester.spi

/** Computes one [Kpi] from a planner response, or null when the KPI does not apply to that response. */
interface KPICalculator {
    fun calculate(response: TravelPlannerResponse): Kpi?
}
