package org.opentripplanner.trakpi.otp.testset.transforms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.opentripplanner.trakpi.otp.kpi.DepartureCountKPICalculator
import org.opentripplanner.trakpi.otp.kpi.FastestItineraryKPICalculator
import org.opentripplanner.trakpi.otp.kpi.ItineraryCountKPICalculator
import org.opentripplanner.trakpi.otp.kpi.MinTransfersKPICalculator
import org.opentripplanner.trakpi.otp.kpi.RoutingTimeKPICalculator
import org.opentripplanner.trakpi.otp.testset.OtpRequest
import org.opentripplanner.trakpi.otp.testset.OtpRequestCodec
import org.opentripplanner.trakpi.testset.Request

class EnsureKpiFieldsTest {
    private val transform =
        EnsureKpiFields(
            listOf(
                ItineraryCountKPICalculator(),
                RoutingTimeKPICalculator(),
                FastestItineraryKPICalculator(),
                MinTransfersKPICalculator(),
                DepartureCountKPICalculator(),
            )
        )

    private fun apply(body: String) = transform.apply(OtpRequest.parse(Request("req", body))).toRequest().body

    @Test
    fun `adds every routing KPI's fields to a trip request, and nothing departure-only`() {
        val out = apply("{ trip(from: {place: \"A\"}) { tripPatterns { startTime } } }")
        listOf("duration", "legs", "mode", "debugOutput", "totalTime").forEach { assertTrue(it in out, "expected '$it' in:\n$out") }
        assertFalse("estimatedCalls" in out, out) // departure KPI's root fields aren't in a trip request
    }

    @Test
    fun `adds the departure KPI's field to a departures request, and nothing routing-only`() {
        val out = apply("{ stopPlace(id: \"NSR:StopPlace:1\") { name } }")
        assertTrue("estimatedCalls" in out, out)
        assertTrue("realtime" in out, out)
        assertFalse("debugOutput" in out, out) // routing KPIs' root fields aren't in a departures request
    }

    @Test
    fun `leaves an unmeasured request untouched`() {
        val body = "{ nearest(latitude: 59.9, longitude: 10.7) { edges { node { distance } } } }"
        assertFalse("estimatedCalls" in apply(body), body)
        assertFalse("debugOutput" in apply(body), body)
    }

    @Test
    fun `merges into a fragment-contributed field once the request is deserialized`() {
        // The codec inlines fragments on deserialize, so the merge sees estimatedCalls despite the spread.
        val request =
            OtpRequestCodec.deserialize(
                Request("r", "{ quay(id: \"1\") { ...q } } fragment q on Quay { estimatedCalls(numberOfDepartures: 10) { aimedDepartureTime } }")
            )
        val out = transform.apply(request).toRequest().body
        assertTrue("realtime" in out, out)
        assertEquals(1, out.split("estimatedCalls").size - 1, "expected exactly one estimatedCalls in:\n$out")
    }
}
