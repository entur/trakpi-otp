package org.opentripplanner.trakpi.analyzer

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.opentripplanner.trakpi.analyzer.spi.ResultsLoader

class AnalyzerTest {
    private fun run(id: String, version: String, at: String, vararg values: Double) =
        TestRun(id, version, Instant.parse(at), values.map { ResultRecord("r", listOf(KpiValue("rt", it))) })

    private fun loaderOf(vararg runs: TestRun) =
        object : ResultsLoader {
            override fun load(args: String?) = runs.toList()
        }

    @Test
    fun `trend orders runs oldest first and aggregates each KPI`() {
        val analyzer =
            Analyzer(
                loaderOf(
                    run("b", "dev", "2026-07-02T04:00:00Z", 10.0, 20.0, 30.0),
                    run("a", "dev", "2026-07-01T04:00:00Z", 2.0, 4.0),
                )
            )

        val trend = analyzer.trend(null)

        assertEquals(listOf("a", "b"), trend.runs.map { it.runId })
        val first = trend.runs.first().kpis.single()
        assertEquals("rt", first.name)
        assertEquals(2, first.count)
        assertEquals(3.0, first.mean)
        assertEquals(20.0, trend.runs[1].kpis.single().mean)
    }

    @Test
    fun `zero values are counted`() {
        val analyzer = Analyzer(loaderOf(run("a", "dev", "2026-07-01T04:00:00Z", 0.0, 0.0, 5.0)))

        val summary = analyzer.trend(null).runs.single().kpis.single()

        assertEquals(2, summary.zeroCount)
        assertEquals(3, summary.count)
    }

    @Test
    fun `diff compares the latest run of each version by mean`() {
        val analyzer =
            Analyzer(
                loaderOf(
                    run("old", "A", "2026-07-01T04:00:00Z", 100.0),
                    run("new", "A", "2026-07-03T04:00:00Z", 120.0),
                    run("base", "B", "2026-07-02T04:00:00Z", 100.0),
                )
            )

        val diff = analyzer.diff(loaderArgs = null, version = "A", baseline = "B")

        val row = diff.rows.single()
        assertEquals("new", diff.version.runId)
        assertEquals(120.0, row.versionMean)
        assertEquals(100.0, row.baselineMean)
        assertEquals(20.0, row.delta)
        assertEquals(20.0, row.pctChange)
    }

    @Test
    fun `diff fails for an unknown baseline`() {
        val analyzer = Analyzer(loaderOf(run("a", "A", "2026-07-01T04:00:00Z", 1.0)))

        assertFailsWith<IllegalArgumentException> { analyzer.diff(null, version = "A", baseline = "does-not-exist") }
    }
}
