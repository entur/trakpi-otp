package org.opentripplanner.trakpi.otp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

class OtpTripTest {
    private fun response(raw: String) = TravelPlannerResponse(raw, success = false, method = "trip")

    @Test
    fun `reads the trip patterns of a trip response`() {
        val response = response("""{"data":{"trip":{"tripPatterns":[{"duration":60}]}}}""")
        assertEquals(1, response.tripPatterns().size)
    }

    @Test
    fun `a null trip yields no trip object`() {
        val response = response("""{"data":{"trip":null},"errors":[{"message":"boom"}]}""")
        assertNull(response.tripObject())
        assertTrue(response.tripPatterns().isEmpty())
    }

    @Test
    fun `null data yields no trip object`() {
        assertNull(response("""{"data":null,"errors":[{"message":"boom"}]}""").tripObject())
    }

    @Test
    fun `a non-JSON body yields no trip object`() {
        assertNull(response("<html>502 Bad Gateway</html>").tripObject())
    }
}
