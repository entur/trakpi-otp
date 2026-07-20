package org.opentripplanner.trakpi.otp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OtpRequestBodyTest {
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
