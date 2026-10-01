package org.opentripplanner.trakpi.otp.kpi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

class GraphQlErrorCountKPICalculatorTest {
    private val calc = GraphQlErrorCountKPICalculator()

    private fun response(raw: String) = TravelPlannerResponse(raw, success = false, method = "trip")

    @Test
    fun `a response without errors scores 0`() {
        assertEquals(0.0, calc.calculate(response("""{"data":{"trip":{"tripPatterns":[]}}}"""))?.value)
    }

    @Test
    fun `counts each error`() {
        val raw = """{"data":{"trip":null},"errors":[{"message":"a"},{"message":"b"}]}"""
        assertEquals(2.0, calc.calculate(response(raw))?.value)
    }

    @Test
    fun `a non-JSON body yields no KPI`() {
        assertNull(calc.calculate(response("<html>502 Bad Gateway</html>")))
    }

    @Test
    fun `the KPI is named graphqlErrorCount`() {
        assertEquals("graphqlErrorCount", calc.calculate(response("""{"data":{}}"""))?.name)
    }
}
