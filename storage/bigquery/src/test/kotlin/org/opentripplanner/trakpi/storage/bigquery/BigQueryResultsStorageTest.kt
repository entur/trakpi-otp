package org.opentripplanner.trakpi.storage.bigquery

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.opentripplanner.trakpi.tester.spi.Kpi
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult

class BigQueryResultsStorageTest {
    private val run = RunMetadata.create(version = "dev", startedAt = Instant.parse("2026-07-08T04:00:00Z"))

    @Test
    fun `maps each KPI to one row carrying the run and request dimensions`() {
        val result =
            TestCaseResult("request-001", "{}", listOf(Kpi("routingTimeMs", 98.1), Kpi("itineraryCount", 5.0)))

        val rows = BigQueryResultsStorage.toRows(run, result)

        assertEquals(2, rows.size)
        val row = rows.first()
        assertEquals("${run.runId}:request-001:routingTimeMs", row.insertId)
        assertEquals(
            mapOf(
                "run_id" to run.runId,
                "version" to "dev",
                "run_ts" to "2026-07-08T04:00:00Z",
                "request_id" to "request-001",
                "kpi_name" to "routingTimeMs",
                "value" to 98.1,
            ),
            row.content,
        )
        assertEquals("itineraryCount", rows[1].content["kpi_name"])
    }

    @Test
    fun `a result with no KPIs yields no rows`() {
        assertTrue(BigQueryResultsStorage.toRows(run, TestCaseResult("r", "{}", emptyList())).isEmpty())
    }
}
