package org.opentripplanner.trakpi.storage.gcs

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.opentripplanner.trakpi.tester.spi.RunMetadata

class GcsResultsWriterTest {
    private val run =
        RunMetadata.create(
            version = "2.10.0-entur-134",
            application = "otp",
            startedAt = Instant.parse("2026-07-08T04:00:00Z"),
            testsetVersion = "testset-1",
            referenceVersion = "baseline",
        )

    @Test
    fun `keys a request by testset and request, shared across runs`() {
        assertEquals("requests/testset-1/request-001", GcsResultsWriter.requestObjectName(run, "request-001"))
    }

    @Test
    fun `keys a response by testset, build version and request`() {
        assertEquals("results/testset-1/2.10.0-entur-134/request-001", GcsResultsWriter.responseObjectName(run, "request-001"))
    }
}
