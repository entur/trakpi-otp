package org.opentripplanner.trakpi.otp.kpi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

class MinTransferTimeKPICalculatorTest {
    private val calc = MinTransferTimeKPICalculator()

    private fun leg(ride: Boolean, start: String, end: String) =
        """{"ride":$ride,"aimedStartTime":"2026-09-01T$start:00+02:00","aimedEndTime":"2026-09-01T$end:00+02:00"}"""

    private fun pattern(vararg legs: String) = """{"legs":[${legs.joinToString(",")}]}"""

    private fun trip(vararg patterns: String) =
        TravelPlannerResponse("""{"data":{"trip":{"tripPatterns":[${patterns.joinToString(",")}]}}}""", success = true, method = "trip")

    @Test
    fun `takes the shortest transfer across itineraries, walk included`() {
        val response =
            trip(
                pattern(leg(true, "08:00", "08:20"), leg(false, "08:20", "08:25"), leg(true, "08:30", "09:00")),
                pattern(leg(true, "08:00", "08:15"), leg(true, "08:22", "08:40"), leg(true, "08:44", "09:10")),
            )
        assertEquals(240.0, calc.calculate(response)?.value)
    }

    @Test
    fun `access and egress walks are not transfers`() {
        val response = trip(pattern(leg(false, "07:50", "08:00"), leg(true, "08:00", "08:20"), leg(false, "08:20", "08:21")))
        assertNull(calc.calculate(response))
    }

    @Test
    fun `no itineraries yields no KPI`() {
        assertNull(calc.calculate(trip()))
    }

    @Test
    fun `a leg without ride yields no KPI`() {
        val response = trip(pattern(leg(true, "08:00", "08:20"), """{"aimedStartTime":"2026-09-01T08:30:00+02:00"}"""))
        assertNull(calc.calculate(response))
    }

    @Test
    fun `the KPI is named minTransferTimeSeconds`() {
        val response = trip(pattern(leg(true, "08:00", "08:20"), leg(true, "08:25", "08:40")))
        assertEquals("minTransferTimeSeconds", calc.calculate(response)?.name)
    }
}
