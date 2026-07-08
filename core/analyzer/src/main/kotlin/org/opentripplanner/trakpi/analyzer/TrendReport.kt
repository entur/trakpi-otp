package org.opentripplanner.trakpi.analyzer

import java.time.Instant

/** KPI trends across a time-ordered series of runs. */
data class TrendReport(val runs: List<RunSummary>)

/** The per-KPI aggregates for one run in a trend. */
data class RunSummary(val runId: String, val version: String, val timestamp: Instant, val kpis: List<KpiSummary>)

/** KPI comparison of one run against a baseline run. */
data class DiffReport(val version: RunSummary, val baseline: RunSummary, val rows: List<DiffRow>)

/** One KPI's change from baseline to version, by mean. */
data class DiffRow(val name: String, val baselineMean: Double, val versionMean: Double) {
    val delta: Double
        get() = versionMean - baselineMean

    /** Percentage change from baseline, or null when the baseline mean is zero. */
    val pctChange: Double?
        get() = if (baselineMean == 0.0) null else delta / baselineMean * 100.0
}
