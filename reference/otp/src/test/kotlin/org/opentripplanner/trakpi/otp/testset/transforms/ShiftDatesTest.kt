package org.opentripplanner.trakpi.otp.testset.transforms

import java.time.Period
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.opentripplanner.trakpi.otp.OtpRequestBody
import org.opentripplanner.trakpi.otp.testset.OtpRequest
import org.opentripplanner.trakpi.testset.Request

class ShiftDatesTest {
    private val transform = ShiftDates(shift = Period.ofWeeks(2))

    private fun shift(body: String): OtpRequestBody = OtpRequestBody.parse(transform.apply(OtpRequest.parse(Request("r", body))).toRequest().body)

    private fun variable(body: OtpRequestBody, name: String): String = body.variables!!.jsonObject.getValue(name).jsonPrimitive.content

    private fun envelope(query: String, variables: String) = "{\"query\":${JsonPrimitive(query)},\"variables\":$variables}"

    @Test
    fun `moves an inline trip dateTime two weeks ahead`() {
        val out = shift("{ trip(from: {place: \"A\"}, to: {place: \"B\"}, dateTime: \"2026-10-21T17:00:00+02:00\") { tripPatterns { duration } } }")
        assertTrue("dateTime: \"2026-11-04T17:00+01:00\"" in out.query, out.query)
    }

    @Test
    fun `keeps the Oslo time of day across a daylight-saving change whatever offset the value has`() {
        // 15:00Z is 17:00 in Oslo before the change on 2026-10-25, and 16:00Z after it.
        val out = shift(envelope("query(\$when: DateTime) { trip(dateTime: \$when) { tripPatterns { duration } } }", "{\"when\":\"2026-10-21T15:00:00Z\"}"))
        assertEquals("2026-11-04T17:00+01:00", variable(out, "when"))
    }

    @Test
    fun `moves dates and date-times in variables whatever they are called`() {
        val out =
            shift(
                envelope(
                    "query(\$d: Date, \$t: DateTime) { serviceJourney(id: \"X\") { estimatedCalls(date: \$d) { aimedDepartureTime } } }",
                    "{\"d\":\"2026-10-20\",\"t\":\"2026-10-20T08:00:00\",\"id\":\"VYG:DatedServiceJourney:391_26-10-20\"}",
                )
            )
        assertEquals("2026-11-03", variable(out, "d"))
        assertEquals("2026-11-03T08:00", variable(out, "t"))
        assertEquals("VYG:DatedServiceJourney:391_26-10-20", variable(out, "id"))
    }

    @Test
    fun `moves a variable's default value`() {
        val out = shift("query(\$dateTime: DateTime = \"2026-10-21T17:00:00+02:00\") { trip(dateTime: \$dateTime) { tripPatterns { duration } } }")
        assertTrue("\"2026-11-04T17:00+01:00\"" in out.query, out.query)
    }

    @Test
    fun `leaves a request without a time unchanged`() {
        val query = "{ trip(from: {place: \"A\"}, to: {place: \"B\"}) { tripPatterns { duration } } }"
        val untouched = OtpRequestBody.parse(OtpRequest.parse(Request("r", query)).toRequest().body)
        assertEquals(untouched, shift(query))
    }
}
