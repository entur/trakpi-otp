package org.opentripplanner.trakpi.otp.testset

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.opentripplanner.trakpi.testset.Request

class OtpRequestCodecTest {
    @Test
    fun `decodes a stored request and encodes it back, preserving id and query`() {
        val encoded = OtpRequestCodec.serialize(OtpRequestCodec.deserialize(Request("r", "{ trip { x } }")))
        assertEquals("r", encoded.id)
        assertTrue("trip" in encoded.body, encoded.body)
    }
}
