package org.opentripplanner.trakpi.otp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.opentripplanner.trakpi.tester.spi.RequestFile

class OtpRequestLoaderTest {
    private val loader = OtpRequestLoader()

    @Test
    fun `treats a bare GraphQL query as a self-contained query`() {
        val body = "{ trip(from: {place: \"A\"}, to: {place: \"B\"}) { tripPatterns { duration } } }"

        val request = loader.load(RequestFile(id = "req", body = body))

        assertEquals(body, request.query)
        assertNull(request.variables)
    }

    @Test
    fun `extracts query and variables from a prod rawQuery object`() {
        val body = """{"query":"query(${'$'}from: Location!){ trip(from: ${'$'}from) { tripPatterns { duration } } }","variables":{"from":{"place":"NSR:StopPlace:1"},"numTripPatterns":10}}"""

        val request = loader.load(RequestFile(id = "req", body = body))

        assertEquals("query(\$from: Location!){ trip(from: \$from) { tripPatterns { duration } } }", request.query)
        assertEquals("NSR:StopPlace:1", request.variables!!.jsonObject["from"]!!.jsonObject["place"]!!.jsonPrimitive.content)
    }

    @Test
    fun `handles a rawQuery object without variables`() {
        val body = """{"query":"{ stopPlace(id: \"NSR:StopPlace:1\") { estimatedCalls { realtime } } }"}"""

        val request = loader.load(RequestFile(id = "req", body = body))

        assertEquals("{ stopPlace(id: \"NSR:StopPlace:1\") { estimatedCalls { realtime } } }", request.query)
        assertNull(request.variables)
    }
}
