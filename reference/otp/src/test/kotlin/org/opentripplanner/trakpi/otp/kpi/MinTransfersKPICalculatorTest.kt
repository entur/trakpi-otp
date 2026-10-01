package org.opentripplanner.trakpi.otp.kpi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

class MinTransfersKPICalculatorTest {
    private val calc = MinTransfersKPICalculator()

    private fun leg(mode: String, ride: Boolean) = """{"mode":"$mode","ride":$ride}"""

    private fun pattern(vararg legs: String) = """{"legs":[${legs.joinToString(",")}]}"""

    private fun trip(vararg patterns: String) =
        TravelPlannerResponse("""{"data":{"trip":{"tripPatterns":[${patterns.joinToString(",")}]}}}""", success = true, method = "trip")

    @Test
    fun `takes the fewest transfers across itineraries`() {
        val response =
            trip(
                pattern(leg("foot", false), leg("bus", true), leg("foot", false), leg("rail", true), leg("bus", true)),
                pattern(leg("bus", true), leg("foot", false), leg("rail", true)),
            )
        assertEquals(1.0, calc.calculate(response)?.value)
    }

    @Test
    fun `non-transit street legs are not counted as rides`() {
        val response = trip(pattern(leg("bicycle", false), leg("rail", true), leg("scooter", false)))
        assertEquals(0.0, calc.calculate(response)?.value)
    }

    @Test
    fun `an itinerary without rides has no transfers`() {
        assertEquals(0.0, calc.calculate(trip(pattern(leg("foot", false))))?.value)
    }

    @Test
    fun `no itineraries yields no KPI`() {
        assertNull(calc.calculate(trip()))
    }

    @Test
    fun `a leg without ride yields no KPI`() {
        assertNull(calc.calculate(trip(pattern("""{"mode":"bus"}"""))))
    }
}
