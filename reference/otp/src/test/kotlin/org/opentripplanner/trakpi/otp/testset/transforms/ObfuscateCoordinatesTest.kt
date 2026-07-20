package org.opentripplanner.trakpi.otp.testset.transforms

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.opentripplanner.trakpi.otp.testset.OtpRequest
import org.opentripplanner.trakpi.testset.Request

class ObfuscateCoordinatesTest {
    // A snapper that snaps every coordinate to one fixed station, so obfuscated points cluster around it.
    private val station = Coordinate(60.0, 10.0)
    private val transform = ObfuscateCoordinates(snapper = { station }, random = Random(1), maxMeters = 500.0)

    private fun obfuscate(body: String): String = transform.apply(OtpRequest.parse(Request("r", body))).toRequest().body

    private fun valuesOf(field: String, text: String): List<Double> =
        Regex("$field\"?\\s*:\\s*(-?[0-9.]+)").findAll(text).map { it.groupValues[1].toDouble() }.toList()

    private fun assertNearStation(out: String) {
        valuesOf("latitude", out).forEach { assertTrue(abs(it - station.latitude) < 0.01, "latitude $it not near station:\n$out") }
        valuesOf("longitude", out).forEach { assertTrue(abs(it - station.longitude) < 0.02, "longitude $it not near station:\n$out") }
    }

    @Test
    fun `obfuscates both nested trip coordinates`() {
        val out =
            obfuscate(
                "{ trip(from: {coordinates: {latitude: 59.91, longitude: 10.75}}, to: {coordinates: {latitude: 63.43, longitude: 10.4}}) { tripPatterns { duration } } }"
            )
        assertEquals(2, valuesOf("latitude", out).size, out) // both from and to were moved
        assertNearStation(out)
        assertFalse("59.91" in out || "63.43" in out, "original coordinates should be gone:\n$out")
    }

    @Test
    fun `obfuscates a nearest query's own arguments`() {
        val out = obfuscate("{ nearest(latitude: 59.91, longitude: 10.75) { edges { node { distance } } } }")
        assertEquals(1, valuesOf("latitude", out).size, out)
        assertNearStation(out)
        assertFalse("59.91" in out, out)
    }

    @Test
    fun `obfuscates coordinates carried in variables`() {
        val body =
            "{\"query\":\"query(\$from: Location!){ trip(from: \$from){ tripPatterns { duration } } }\"," +
                "\"variables\":{\"from\":{\"coordinates\":{\"latitude\":59.91,\"longitude\":10.75}}}}"
        val out = obfuscate(body)
        assertEquals(1, valuesOf("latitude", out).size, out)
        assertNearStation(out)
        assertFalse("59.91" in out, out)
    }

    @Test
    fun `leaves a request without coordinates untouched`() {
        val out = obfuscate("{ stopPlace(id: \"NSR:StopPlace:1\") { name } }")
        assertTrue(valuesOf("latitude", out).isEmpty(), out)
        assertTrue("NSR:StopPlace:1" in out, out)
    }
}
