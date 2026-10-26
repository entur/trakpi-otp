package org.opentripplanner.trakpi.tester.spi.kpi

import org.opentripplanner.trakpi.tester.spi.TravelPlannerRequest
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/**
 * Computes one [Kpi] from a planner's response to a request. Implement [ResponseKPICalculator] when the KPI
 * reads only the response, or [RequestResponseKPICalculator] when it also reads the request.
 */
sealed interface KPICalculator

/** Computes the [Kpi] from [request] and its [response], passing each calculator only what it reads. */
internal fun KPICalculator.calculate(request: TravelPlannerRequest, response: TravelPlannerResponse): Kpi? =
    when (this) {
        is ResponseKPICalculator -> calculate(response)
        is RequestResponseKPICalculator -> calculate(request, response)
    }
