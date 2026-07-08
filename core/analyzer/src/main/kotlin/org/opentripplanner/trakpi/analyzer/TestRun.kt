package org.opentripplanner.trakpi.analyzer

import java.time.Instant

/**
 * One stored test run, as reconstructed from persisted results. The contract between the writer and
 * the analyzer is the persisted result, not a shared type, so the analyzer keeps its own read-model.
 */
data class TestRun(
    val runId: String,
    val version: String,
    val timestamp: Instant,
    val results: List<ResultRecord>,
)

/** One request's stored outcome. The raw response is not needed for trend analysis, so it is omitted. */
data class ResultRecord(val requestId: String, val kpis: List<KpiValue>)

/** A single KPI value read back from storage. */
data class KpiValue(val name: String, val value: Double)
