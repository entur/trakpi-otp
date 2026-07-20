package org.opentripplanner.trakpi.otp.testset

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.opentripplanner.trakpi.testset.Request

class OtpRequestCodecTest {
    @Test
    fun `decodes a stored request and encodes it back, preserving id and query`() {
        val encoded = OtpRequestCodec.serialize(OtpRequestCodec.deserialize(Request("r", "{ trip { x } }")))
        assertEquals("r", encoded.id)
        assertTrue("trip" in encoded.body, encoded.body)
    }

    @Test
    fun `deserialize inlines fragments so the working request is fragment-free`() {
        val body = OtpRequestCodec.deserialize(Request("r", "{ quay(id: \"1\") { ...q } } fragment q on Quay { estimatedCalls { realtime } }")).toRequest().body
        assertTrue("estimatedCalls" in body && "realtime" in body, body)
        assertFalse("...q" in body || "fragment q" in body, "fragments should be inlined on deserialize:\n$body")
    }
}
