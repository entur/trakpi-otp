package org.opentripplanner.trakpi.otp.kpi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

class MinStreetDistanceKPICalculatorTest {
    private val calc = MinStreetDistanceKPICalculator()

    private fun pattern(streetDistance: Double, vararg rides: Boolean) =
        """{"streetDistance":$streetDistance,"legs":[${rides.joinToString(",") { """{"ride":$it}""" }}]}"""

    private fun trip(vararg patterns: String) =
        TravelPlannerResponse("""{"data":{"trip":{"tripPatterns":[${patterns.joinToString(",")}]}}}""", success = true, method = "trip")

    @Test
    fun `takes the shortest street distance across transit itineraries`() {
        val response = trip(pattern(850.0, false, true, false), pattern(420.5, true, false))
        assertEquals(420.5, calc.calculate(response)?.value)
    }

    @Test
    fun `a street-only itinerary is left out`() {
        val response = trip(pattern(1200.0, false), pattern(600.0, false, true, false))
        assertEquals(600.0, calc.calculate(response)?.value)
    }

    @Test
    fun `only street-only itineraries yield no KPI`() {
        assertNull(calc.calculate(trip(pattern(1200.0, false))))
    }

    @Test
    fun `no itineraries yields no KPI`() {
        assertNull(calc.calculate(trip()))
    }

    @Test
    fun `a leg without ride yields no KPI`() {
        assertNull(calc.calculate(trip("""{"streetDistance":100.0,"legs":[{"mode":"bus"}]}""")))
    }

    @Test
    fun `the KPI is named minStreetDistanceMeters`() {
        assertEquals("minStreetDistanceMeters", calc.calculate(trip(pattern(1.0, true)))?.name)
    }
}
