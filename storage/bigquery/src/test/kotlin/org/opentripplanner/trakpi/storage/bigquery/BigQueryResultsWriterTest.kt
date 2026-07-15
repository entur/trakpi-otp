package org.opentripplanner.trakpi.storage.bigquery

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.opentripplanner.trakpi.tester.spi.Kpi
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult

class BigQueryResultsWriterTest {
    private val run =
        RunMetadata.create(
            version = "dev",
            application = "otp",
            startedAt = Instant.parse("2026-07-08T04:00:00Z"),
            referenceVersion = "baseline",
            testsetVersion = "testset-1",
        )

    private fun result(kpis: List<Kpi>) =
        TestCaseResult(
            requestId = "request-001",
            request = "{}",
            method = "trip",
            success = true,
            rawResponse = "{}",
            attributes = mapOf("http_status_code" to "200", "http_status_class" to "2xx"),
            kpis = kpis,
        )

    @Test
    fun `maps each KPI to one row carrying the run and request dimensions`() {
        val rows = BigQueryResultsWriter.toRows(run, result(listOf(Kpi("routingTimeMs", 98.1), Kpi("itineraryCount", 5.0))))

        assertEquals(2, rows.size)
        val row = rows.first()
        assertEquals("${run.runId}:request-001:routingTimeMs", row.insertId)
        assertEquals(
            mapOf(
                "run_id" to run.runId,
                "version" to "dev",
                "application" to "otp",
                "run_ts" to "2026-07-08T04:00:00Z",
                "is_reference_version" to false,
                "reference_version" to "baseline",
                "testset_version" to "testset-1",
                "request_id" to "request-001",
                "method" to "trip",
                "success" to true,
                "http_status_code" to "200",
                "http_status_class" to "2xx",
                "kpi_name" to "routingTimeMs",
                "value" to 98.1,
            ),
            row.content,
        )
        assertEquals("itineraryCount", rows[1].content["kpi_name"])
    }

    @Test
    fun `a result with no KPIs yields one dimension-only row`() {
        val rows = BigQueryResultsWriter.toRows(run, result(emptyList()))

        assertEquals(1, rows.size)
        assertEquals("${run.runId}:request-001", rows.first().insertId)
        assertTrue("kpi_name" !in rows.first().content)
        assertTrue("value" !in rows.first().content)
        assertEquals("trip", rows.first().content["method"])
    }
}
