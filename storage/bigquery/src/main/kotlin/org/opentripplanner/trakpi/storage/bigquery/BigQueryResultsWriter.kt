package org.opentripplanner.trakpi.storage.bigquery

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.TableId
import java.time.Instant
import org.opentripplanner.trakpi.tester.spi.ResultsWriter
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.RunPhase
import org.opentripplanner.trakpi.tester.spi.TestCaseResult

/**
 * Streams each result into a BigQuery table, one row per KPI value. Every row carries the run and
 * request dimensions (`run_id, version, application, run_ts, is_reference_version, reference_version,
 * testset_version, origin, label, request_id, method, success`) plus the result's implementation-specific
 * attributes (e.g. `http_status_code`), so each row is self-contained. A result that carries no
 * KPIs still yields one dimension-only row with null `kpi_name`/`value`.
 * Assumes the table already exists with columns for every dimension and attribute.
 *
 * Each finished phase of a run adds one row (`run_id, phase, recorded_at`) to [runsTableId]. A run's rows in
 * [tableId] are complete for a phase only once that row exists.
 *
 * Authenticates with Application Default Credentials.
 */
class BigQueryResultsWriter(
    private val bigQuery: BigQuery,
    private val tableId: TableId,
    private val runsTableId: TableId,
) : ResultsWriter {

    override fun store(run: RunMetadata, result: TestCaseResult) {
        val rows = toRows(run, result)
        if (rows.isEmpty()) return
        val request =
            InsertAllRequest.newBuilder(tableId).apply { rows.forEach { addRow(it.insertId, it.content) } }.build()
        val response = bigQuery.insertAll(request)
        check(!response.hasErrors()) {
            "BigQuery insert failed for run ${run.runId}, request ${result.requestId}: ${response.insertErrors}"
        }
    }

    override fun completeRun(run: RunMetadata, phase: RunPhase) {
        val row = completionRow(run, phase, recordedAt = Instant.now())
        val response = bigQuery.insertAll(InsertAllRequest.newBuilder(runsTableId).addRow(row.insertId, row.content).build())
        check(!response.hasErrors()) { "BigQuery insert failed for the completion of run ${run.runId}, phase $phase: ${response.insertErrors}" }
    }

    companion object {
        /**
         * A storage that streams KPIs into `<projectId>.<dataset>.<table>` and run completions into
         * `<projectId>.<dataset>.<runsTable>`, using Application Default Credentials.
         */
        fun create(projectId: String, dataset: String, table: String, runsTable: String): BigQueryResultsWriter {
            val bigQuery = BigQueryOptions.newBuilder().setProjectId(projectId).build().service
            return BigQueryResultsWriter(bigQuery, TableId.of(projectId, dataset, table), TableId.of(projectId, dataset, runsTable))
        }

        /** The runs-table row recording that [phase] of [run] finished at [recordedAt]. */
        internal fun completionRow(run: RunMetadata, phase: RunPhase, recordedAt: Instant): Row {
            val phaseName = phase.name.lowercase()
            return Row(
                insertId = "${run.runId}:$phaseName",
                content = mapOf("run_id" to run.runId, "phase" to phaseName, "recorded_at" to recordedAt.toString()),
            )
        }

        /**
         * Maps one result to BigQuery rows, one row per KPI, or a single dimension-only row when the
         * result carries no KPIs. The run and request dimensions and the result's attributes are
         * denormalized onto every row.
         */
        internal fun toRows(run: RunMetadata, result: TestCaseResult): List<Row> {
            val dimensions =
                buildMap<String, Any> {
                    put("run_id", run.runId)
                    put("version", run.version.value)
                    put("application", run.application)
                    put("run_ts", run.startedAt.toString())
                    put("is_reference_version", run.isReferenceVersion)
                    run.referenceVersion?.let { put("reference_version", it.value) }
                    put("testset_version", run.testsetVersion.value)
                    put("origin", run.origin)
                    run.label?.let { put("label", it) }
                    put("request_id", result.requestId)
                    put("method", result.method)
                    put("success", result.success)
                    result.attributes.forEach { (name, value) -> put(name, value) }
                }
            if (result.kpis.isEmpty()) {
                return listOf(Row(insertId = "${run.runId}:${result.requestId}", content = dimensions))
            }
            return result.kpis.map { kpi ->
                Row(
                    insertId = "${run.runId}:${result.requestId}:${kpi.name}",
                    content = dimensions + mapOf("kpi_name" to kpi.name, "value" to kpi.value),
                )
            }
        }
    }

    /** An insert id paired with the column values for one BigQuery row. */
    internal data class Row(val insertId: String, val content: Map<String, Any>)
}
