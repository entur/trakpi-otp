package org.opentripplanner.trakpi.storage.bigquery

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.opentripplanner.trakpi.common.PlannerVersion
import org.opentripplanner.trakpi.common.TestsetVersion
import org.opentripplanner.trakpi.tester.spi.kpi.Kpi
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult

class BigQueryResultsWriterTest {
    private val run =
        RunMetadata.create(
            version = PlannerVersion("dev"),
            application = "otp",
            startedAt = Instant.parse("2026-07-08T04:00:00Z"),
            referenceVersion = PlannerVersion("baseline"),
            testsetVersion = TestsetVersion("testset-1"),
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
                "origin" to "local",
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
    fun `origin and label from the run appear on every row, label only when set`() {
        val tagged =
            RunMetadata.create(
                version = PlannerVersion("dev"),
                application = "otp",
                startedAt = Instant.parse("2026-07-08T04:00:00Z"),
                testsetVersion = TestsetVersion("testset-1"),
                origin = "manual",
                label = "dts-ab",
            )

        val row = BigQueryResultsWriter.toRows(tagged, result(listOf(Kpi("itineraryCount", 5.0)))).first()
        assertEquals("manual", row.content["origin"])
        assertEquals("dts-ab", row.content["label"])

        // The default (untagged) run has no label, so the column is omitted rather than written null.
        val untagged = BigQueryResultsWriter.toRows(run, result(listOf(Kpi("itineraryCount", 5.0)))).first()
        assertEquals("local", untagged.content["origin"])
        assertTrue("label" !in untagged.content)
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
