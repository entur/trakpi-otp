package org.opentripplanner.trakpi.otp.kpi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

class MinGeneralizedCostKPICalculatorTest {
    private val calc = MinGeneralizedCostKPICalculator()

    private fun trip(vararg patterns: String) =
        TravelPlannerResponse("""{"data":{"trip":{"tripPatterns":[${patterns.joinToString(",")}]}}}""", success = true, method = "trip")

    @Test
    fun `takes the lowest cost across itineraries`() {
        val response = trip("""{"generalizedCost":2400}""", """{"generalizedCost":1800}""", """{"generalizedCost":null}""")
        assertEquals(1800.0, calc.calculate(response)?.value)
    }

    @Test
    fun `no itineraries yields no KPI`() {
        assertNull(calc.calculate(trip()))
    }

    @Test
    fun `itineraries without a cost yield no KPI`() {
        assertNull(calc.calculate(trip("""{"duration":60}""")))
    }

    @Test
    fun `the KPI is named minGeneralizedCost`() {
        assertEquals("minGeneralizedCost", calc.calculate(trip("""{"generalizedCost":1}"""))?.name)
    }
}
