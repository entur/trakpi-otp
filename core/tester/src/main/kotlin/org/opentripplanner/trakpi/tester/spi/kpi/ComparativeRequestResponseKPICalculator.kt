package org.opentripplanner.trakpi.tester.spi.kpi

import org.opentripplanner.trakpi.tester.spi.TravelPlannerRequest
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/** A [ComparativeKPICalculator] that reads the request as well as the responses. */
fun interface ComparativeRequestResponseKPICalculator : ComparativeKPICalculator {
    /**
     * Computes the [Kpi] by comparing [subject] against [reference], both responses to [request], or null
     * when the comparison does not apply to them.
     */
    fun calculate(request: TravelPlannerRequest, subject: TravelPlannerResponse, reference: TravelPlannerResponse): Kpi?
}
