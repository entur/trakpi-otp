package org.opentripplanner.trakpi.otp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OtpRequestBodyTest {
    @Test
    fun `treats a bare GraphQL query as a self-contained query`() {
        val body = "{ trip(from: {place: \"A\"}, to: {place: \"B\"}) { tripPatterns { duration } } }"

        val parsed = OtpRequestBody.parse(body)

        assertEquals(body, parsed.query)
        assertNull(parsed.variables)
    }

    @Test
    fun `extracts query and variables from a prod rawQuery object`() {
        val body = """{"query":"query(${'$'}from: Location!){ trip(from: ${'$'}from) { tripPatterns { duration } } }","variables":{"from":{"place":"NSR:StopPlace:1"},"numTripPatterns":10}}"""

        val parsed = OtpRequestBody.parse(body)

        assertEquals("query(\$from: Location!){ trip(from: \$from) { tripPatterns { duration } } }", parsed.query)
        assertEquals("NSR:StopPlace:1", parsed.variables!!.jsonObject["from"]!!.jsonObject["place"]!!.jsonPrimitive.content)
    }

    @Test
    fun `explicit null variables is treated as no variables and serializes to a bare query`() {
        val parsed = OtpRequestBody.parse("""{"query":"{ trip { x } }","variables":null}""")
        assertNull(parsed.variables)
        assertEquals("{ trip { x } }", parsed.serialize())
    }

    @Test
    fun `a missing variables key means no variables`() {
        assertNull(OtpRequestBody.parse("""{"query":"{ trip { x } }"}""").variables)
    }

    @Test
    fun `real variables are preserved and round-trip through serialize`() {
        val parsed = OtpRequestBody.parse("""{"query":"{ trip { x } }","variables":{"from":"A"}}""")
        assertNotNull(parsed.variables)
        assertEquals(parsed, OtpRequestBody.parse(parsed.serialize()))
    }
}
