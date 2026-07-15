package org.opentripplanner.trakpi.storage.gcs

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.opentripplanner.trakpi.tester.spi.RunMetadata

class GcsResultsWriterTest {
    private val run =
        RunMetadata.create(
            version = "dev",
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
    fun `keys a response by testset, run and request`() {
        assertEquals("results/testset-1/${run.runId}/request-001", GcsResultsWriter.responseObjectName(run, "request-001"))
    }
}
