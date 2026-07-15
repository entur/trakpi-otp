package org.opentripplanner.trakpi.tester.spi

interface ComparativeKPICalculator {
    /**
     * Computes one [Kpi] by comparing the [subject] response just produced against the [reference]
     * response the reference run produced for the same request, or null when the comparison does not
     * apply to these responses. Mirrors [KPICalculator], but over two responses instead of one.
     */
    fun calculate(subject: TravelPlannerResponse, reference: TravelPlannerResponse): Kpi?
}
