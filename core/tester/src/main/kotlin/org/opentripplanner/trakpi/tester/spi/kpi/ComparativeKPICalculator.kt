package org.opentripplanner.trakpi.tester.spi.kpi

import org.opentripplanner.trakpi.tester.spi.TravelPlannerRequest
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/**
 * Computes one [Kpi] by comparing the subject response just produced against the reference response the
 * reference run produced for the same request. Mirrors [KPICalculator], but over two responses instead of
 * one. Implement [ComparativeResponseKPICalculator] when the comparison reads only the responses, or
 * [ComparativeRequestResponseKPICalculator] when it also reads the request.
 */
sealed interface ComparativeKPICalculator

/** Computes the [Kpi] for [request] from [subject] and [reference], passing each calculator only what it reads. */
internal fun ComparativeKPICalculator.calculate(
    request: TravelPlannerRequest,
    subject: TravelPlannerResponse,
    reference: TravelPlannerResponse,
): Kpi? =
    when (this) {
        is ComparativeResponseKPICalculator -> calculate(subject, reference)
        is ComparativeRequestResponseKPICalculator -> calculate(request, subject, reference)
    }
