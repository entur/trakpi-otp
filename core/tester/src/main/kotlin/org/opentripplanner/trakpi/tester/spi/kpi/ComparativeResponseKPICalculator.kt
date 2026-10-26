package org.opentripplanner.trakpi.tester.spi.kpi

import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/** A [ComparativeKPICalculator] that reads only the responses. */
fun interface ComparativeResponseKPICalculator : ComparativeKPICalculator {
    /**
     * Computes the [Kpi] by comparing [subject] against [reference], or null when the comparison does not
     * apply to these responses.
     */
    fun calculate(subject: TravelPlannerResponse, reference: TravelPlannerResponse): Kpi?
}
