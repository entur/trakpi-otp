package org.opentripplanner.trakpi.otp.kpi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import org.opentripplanner.trakpi.otp.OtpRequestBody
import org.opentripplanner.trakpi.tester.spi.TravelPlannerRequest
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

class OnBoardHasItinerariesKPICalculatorTest {
    private val calc = OnBoardHasItinerariesKPICalculator()

    private val serviceJourneyLocation =
        """{datedServiceJourneyReference: {serviceJourneyOnServiceDate: {serviceJourneyId: "VYG:ServiceJourney:1", serviceDate: "2026-10-23"}}}"""

    private fun request(query: String, variables: String? = null) =
        TravelPlannerRequest("request-1", OtpRequestBody(query, variables?.let { Json.parseToJsonElement(it) }).serialize())

    private val inlineOnBoard =
        request("""{ trip(from: {serviceJourneyLocation: $serviceJourneyLocation}, to: {place: "NSR:StopPlace:1"}) { tripPatterns { __typename } } }""")

    private fun trip(patternCount: Int) =
        TravelPlannerResponse(
            """{"data":{"trip":{"tripPatterns":[${List(patternCount) { """{"__typename":"TripPattern"}""" }.joinToString(",")}]}}}""",
            success = true,
            method = "trip",
        )

    @Test
    fun `an on-board request with itineraries scores 1`() {
        assertEquals(1.0, calc.calculate(inlineOnBoard, trip(2))?.value)
    }

    @Test
    fun `an on-board request without itineraries scores 0`() {
        assertEquals(0.0, calc.calculate(inlineOnBoard, trip(0))?.value)
    }

    @Test
    fun `a serviceJourneyLocation given as a variable inside from is on-board`() {
        val request =
            request(
                """query(${'$'}sjl: ServiceJourneyLocationInput) { trip(from: {serviceJourneyLocation: ${'$'}sjl}, to: {place: "NSR:StopPlace:1"}) { tripPatterns { __typename } } }""",
                """{"sjl":{"datedServiceJourneyReference":{"datedServiceJourneyId":"VYG:DatedServiceJourney:1"}}}""",
            )
        assertEquals(1.0, calc.calculate(request, trip(1))?.value)
    }

    @Test
    fun `a from location given as a variable is on-board when it sets serviceJourneyLocation`() {
        val query = """query(${'$'}from: Location!) { trip(from: ${'$'}from, to: {place: "NSR:StopPlace:1"}) { tripPatterns { __typename } } }"""
        val onBoard = request(query, """{"from":{"serviceJourneyLocation":{"datedServiceJourneyReference":{"datedServiceJourneyId":"VYG:DatedServiceJourney:1"}}}}""")
        val atStop = request(query, """{"from":{"place":"NSR:StopPlace:2"}}""")

        assertEquals(0.0, calc.calculate(onBoard, trip(0))?.value)
        assertNull(calc.calculate(atStop, trip(0)))
    }

    @Test
    fun `an on-board trip field inside a fragment is found`() {
        val request =
            request(
                """query { ...T } fragment T on QueryType { trip(from: {serviceJourneyLocation: $serviceJourneyLocation}, to: {place: "NSR:StopPlace:1"}) { tripPatterns { __typename } } }"""
            )
        assertEquals(1.0, calc.calculate(request, trip(1))?.value)
    }

    @Test
    fun `a request that does not start on-board yields no KPI`() {
        val request = request("""{ trip(from: {place: "NSR:StopPlace:2"}, to: {place: "NSR:StopPlace:1"}) { tripPatterns { __typename } } }""")
        assertNull(calc.calculate(request, trip(0)))
    }

    @Test
    fun `a null serviceJourneyLocation is not on-board`() {
        val inlineNull = request("""{ trip(from: {place: "NSR:StopPlace:2", serviceJourneyLocation: null}, to: {place: "NSR:StopPlace:1"}) { tripPatterns { __typename } } }""")
        val variableNull =
            request(
                """query(${'$'}from: Location!) { trip(from: ${'$'}from, to: {place: "NSR:StopPlace:1"}) { tripPatterns { __typename } } }""",
                """{"from":{"place":"NSR:StopPlace:2","serviceJourneyLocation":null}}""",
            )

        assertNull(calc.calculate(inlineNull, trip(0)))
        assertNull(calc.calculate(variableNull, trip(0)))
    }

    @Test
    fun `a response without a trip object yields no KPI`() {
        val failed = TravelPlannerResponse("""{"data":{"trip":null},"errors":[{"message":"boom"}]}""", success = true, method = "trip")
        assertNull(calc.calculate(inlineOnBoard, failed))
    }

    @Test
    fun `an unparsable query yields no KPI`() {
        assertNull(calc.calculate(request("{ trip("), trip(1)))
    }

    @Test
    fun `KPI name is onBoardHasItineraries`() {
        assertEquals("onBoardHasItineraries", calc.calculate(inlineOnBoard, trip(1))?.name)
    }
}
