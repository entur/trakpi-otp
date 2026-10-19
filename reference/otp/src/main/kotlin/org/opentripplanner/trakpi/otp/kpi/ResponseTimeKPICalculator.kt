package org.opentripplanner.trakpi.otp.kpi

import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse
import org.opentripplanner.trakpi.tester.spi.kpi.Kpi

/**
 * End-to-end response time as the client measured it, in milliseconds; null when the response carries none.
 */
class ResponseTimeKPICalculator : OtpKPICalculator {
    override fun calculate(response: TravelPlannerResponse): Kpi? =
        response.responseTime?.let { Kpi("responseTimeMs", it.toNanos() / 1_000_000.0) }
}
