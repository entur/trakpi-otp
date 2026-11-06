package org.opentripplanner.trakpi.otp.testset

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.opentripplanner.trakpi.otp.OtpRequestBody
import org.opentripplanner.trakpi.testset.Request

class SearchTimeWriterTest {
    // Tuesday 2026-10-20 08:15 in Oslo (CEST, +02:00).
    private val sentAt = Instant.parse("2026-10-20T06:15:00Z")
    private val writer = SearchTimeWriter()

    private fun write(body: String): OtpRequestBody = OtpRequestBody.parse(writer.write(body, sentAt))

    private fun variable(body: OtpRequestBody, name: String): String = body.variables!!.jsonObject.getValue(name).jsonPrimitive.content

    private fun envelope(query: String, variables: String) = "{\"query\":${JsonPrimitive(query)},\"variables\":$variables}"

    @Test
    fun `writes the sent time into a trip without a dateTime`() {
        val out = write("{ trip(from: {place: \"A\"}, to: {place: \"B\"}) { tripPatterns { duration } } }")
        assertTrue("dateTime: \"2026-10-20T08:15+02:00\"" in out.query, out.query)
    }

    @Test
    fun `keeps a dateTime the trip already has`() {
        val out = write("{ trip(dateTime: \"2026-10-24T17:00:00+02:00\") { tripPatterns { duration } } }")
        assertTrue("dateTime: \"2026-10-24T17:00:00+02:00\"" in out.query, out.query)
        assertTrue("2026-10-20" !in out.query, out.query)
    }

    @Test
    fun `sets a dateTime variable the request leaves unset`() {
        val out = write(envelope("query(\$dateTime: DateTime) { trip(dateTime: \$dateTime) { tripPatterns { duration } } }", "{\"dateTime\":null}"))
        assertEquals("2026-10-20T08:15+02:00", variable(out, "dateTime"))
    }

    @Test
    fun `leaves a dateTime variable with a default value alone`() {
        val out = write("query(\$dateTime: DateTime = \"2026-10-24T17:00:00+02:00\") { trip(dateTime: \$dateTime) { tripPatterns { duration } } }")
        assertEquals(null, out.variables)
        assertTrue("2026-10-20" !in out.query, out.query)
    }

    @Test
    fun `writes the sent time into departures, as a date on a service journey`() {
        val out =
            write(
                "{ stopPlace(id: \"NSR:StopPlace:1\") { estimatedCalls(timeRange: 3600) { aimedDepartureTime } } " +
                    "serviceJourney(id: \"X\") { estimatedCalls { aimedDepartureTime } } }"
            )
        assertTrue("estimatedCalls(timeRange: 3600, startTime: \"2026-10-20T08:15+02:00\")" in out.query, out.query)
        assertTrue("estimatedCalls(date: \"2026-10-20\")" in out.query, out.query)
    }

    @Test
    fun `writes into departures inside a fragment`() {
        val out = write("{ quay(id: \"Q\") { ...calls } } fragment calls on Quay { estimatedCalls { aimedDepartureTime } }")
        assertTrue("startTime: \"2026-10-20T08:15+02:00\"" in out.query, out.query)
    }

    @Test
    fun `leaves a paging trip's time to its cursor`() {
        val out = write(envelope("query(\$c: String) { trip(pageCursor: \$c) { tripPatterns { duration } } }", "{\"c\":\"MXwyMDI2LTEw\"}"))
        assertTrue("dateTime" !in out.query, out.query)
    }

    @Test
    fun `leaves a request without a search time alone`() {
        val body = "{ line(id: \"L\") { name } }"
        assertEquals(OtpRequestCodec.deserialize(Request("", body)).toRequest().body, writer.write(body, sentAt))
    }

    @Test
    fun `returns a body it can't parse as it is`() {
        assertEquals("not graphql {", writer.write("not graphql {", sentAt))
    }
}
