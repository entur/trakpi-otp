package org.opentripplanner.trakpi.otp.kpi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

class ItinerariesMatchReferenceKPICalculatorTest {
    private val calc = ItinerariesMatchReferenceKPICalculator()

    private fun leg(
        mode: String,
        code: String,
        sj: String,
        from: String,
        to: String,
        start: String,
        end: String,
        expectedStart: String = start,
    ) =
        """{"mode":"$mode","aimedStartTime":"$start","aimedEndTime":"$end","expectedStartTime":"$expectedStart",""" +
            """"fromPlace":{"name":"$from"},"toPlace":{"name":"$to"},"line":{"publicCode":"$code"},"serviceJourney":{"id":"$sj"}}"""

    private fun pattern(vararg legs: String) = """{"legs":[${legs.joinToString(",")}]}"""

    private fun trip(vararg patterns: String) =
        TravelPlannerResponse("""{"data":{"trip":{"tripPatterns":[${patterns.joinToString(",")}]}}}""", success = true, method = "trip")

    @Test
    fun `identical itineraries score 1`() {
        val a = trip(pattern(leg("rail", "RE10", "SJ:1", "A", "B", "08:00", "08:30")))
        val b = trip(pattern(leg("rail", "RE10", "SJ:1", "A", "B", "08:00", "08:30")))
        assertEquals(1.0, calc.calculate(a, b)?.value)
    }

    @Test
    fun `a differing leg scores 0`() {
        val subject = trip(pattern(leg("rail", "RE10", "SJ:1", "A", "B", "08:00", "08:30")))
        val reference = trip(pattern(leg("rail", "RE11", "SJ:2", "A", "B", "08:00", "08:30")))
        assertEquals(0.0, calc.calculate(subject, reference)?.value)
    }

    @Test
    fun `a different number of itineraries scores 0`() {
        val subject = trip(pattern(leg("rail", "RE10", "SJ:1", "A", "B", "08:00", "08:30")))
        val reference =
            trip(
                pattern(leg("rail", "RE10", "SJ:1", "A", "B", "08:00", "08:30")),
                pattern(leg("bus", "31", "SJ:9", "A", "B", "08:05", "08:40")),
            )
        assertEquals(0.0, calc.calculate(subject, reference)?.value)
    }

    @Test
    fun `differing realtime (expected) times do not count when aimed times match`() {
        val subject = trip(pattern(leg("rail", "RE10", "SJ:1", "A", "B", "08:00", "08:30", expectedStart = "08:03")))
        val reference = trip(pattern(leg("rail", "RE10", "SJ:1", "A", "B", "08:00", "08:30", expectedStart = "08:00")))
        assertEquals(1.0, calc.calculate(subject, reference)?.value)
    }

    @Test
    fun `two empty responses match`() {
        assertEquals(1.0, calc.calculate(trip(), trip())?.value)
    }

    @Test
    fun `a non-trip response yields no KPI`() {
        val notATrip = TravelPlannerResponse("""{"data":{"stopPlace":{"estimatedCalls":[]}}}""", success = true, method = "stopPlace")
        val aTrip = trip(pattern(leg("rail", "RE10", "SJ:1", "A", "B", "08:00", "08:30")))
        assertNull(calc.calculate(aTrip, notATrip))
        assertNull(calc.calculate(notATrip, aTrip))
    }

    @Test
    fun `the KPI is named itinerariesMatchReference`() {
        val a = trip(pattern(leg("rail", "RE10", "SJ:1", "A", "B", "08:00", "08:30")))
        assertEquals("itinerariesMatchReference", calc.calculate(a, a)?.name)
    }
}
