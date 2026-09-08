package org.opentripplanner.trakpi.tester.spi

/**
 * A run that recorded results for a request: the coordinates that address its archived responses. A
 * comparison run carries both its own [version] and the [referenceVersion] it was scored against.
 */
data class RunRef(
    val runId: String,
    /** When the run started, as an ISO-8601 UTC instant (e.g. "2026-09-04T05:09:38Z") */
    val runTs: String,
    val version: String,
    val referenceVersion: String?,
    val testsetVersion: String,
)

/** A run together with the KPI values it recorded for one request, keyed by KPI name. */
data class RunKpiSnapshot(val run: RunRef, val kpis: Map<String, Double>)

/**
 * Reads run-level data for drilling down into a single request across the runs that exercised it:
 * [runsForRequest] is the run vector (how a request fared across runs, the summary a drill-down starts
 * from), and [resolveRun] turns a run id into the coordinates needed to fetch that run's archived responses.
 */
interface RunReader {
    /** Every run that recorded a result for [requestId], newest first, with that run's KPI values. */
    fun runsForRequest(requestId: String): List<RunKpiSnapshot>

    /** The ref of the run given by its [runId], or null when no run is found. */
    fun resolveRun(runId: String): RunRef?
}
