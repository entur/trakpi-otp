package org.opentripplanner.trakpi.otp.kpi

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse
import org.opentripplanner.trakpi.tester.spi.kpi.Kpi

class ResponseTimeKPICalculatorTest {
    private val calc = ResponseTimeKPICalculator()

    private fun response(responseTime: Duration?, success: Boolean = true) =
        TravelPlannerResponse("""{"data":{}}""", success = success, method = "trip", responseTime = responseTime)

    @Test
    fun `reports the response time in milliseconds`() {
        assertEquals(Kpi("responseTimeMs", 123.5), calc.calculate(response(Duration.ofNanos(123_500_000))))
    }

    @Test
    fun `unsuccessful responses are timed too`() {
        assertEquals(40.0, calc.calculate(response(Duration.ofMillis(40), success = false))?.value)
    }

    @Test
    fun `a response without a response time yields no KPI`() {
        assertNull(calc.calculate(response(null)))
    }
}
