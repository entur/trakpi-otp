package org.opentripplanner.trakpi.storage.gcs

import kotlin.test.Test
import kotlin.test.assertEquals
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

class ResponseJsonTest {
    @Test
    fun `serializes and parses a response back to an equal value`() {
        val response =
            TravelPlannerResponse(
                raw = """{"data":{"trip":{"tripPatterns":[{},{}]}}}""",
                success = true,
                method = "trip",
                attributes = mapOf("http_status_code" to "200", "http_status_class" to "2xx"),
            )

        assertEquals(response, ResponseJson.parse(ResponseJson.serialize(response)))
    }
}
