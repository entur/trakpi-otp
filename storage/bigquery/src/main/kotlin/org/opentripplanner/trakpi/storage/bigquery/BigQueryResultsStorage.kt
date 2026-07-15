package org.opentripplanner.trakpi.storage.bigquery

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.TableId
import org.opentripplanner.trakpi.tester.spi.ResultsStorage
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult

/**
 * Streams each result into a BigQuery table, one row per KPI value. Every row carries the run and
 * request dimensions (`run_id, version, application, run_ts, is_reference_version, reference_version,
 * testset_version, request_id, method, success`) plus the result's implementation-specific
 * attributes (e.g. `http_status_code`), so each row is self-contained. A request that produced no
 * KPIs — typically a failure — still yields one dimension-only row with null `kpi_name`/`value`.
 * Assumes the table already exists with columns for every dimension and attribute.
 * Authenticates with Application Default Credentials.
 */
class BigQueryResultsStorage(private val bigQuery: BigQuery, private val tableId: TableId) : ResultsStorage {

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

    companion object {
        /** A storage that streams into `<projectId>.<dataset>.<table>` using Application Default Credentials. */
        fun create(projectId: String, dataset: String, table: String): BigQueryResultsStorage {
            val bigQuery = BigQueryOptions.newBuilder().setProjectId(projectId).build().service
            return BigQueryResultsStorage(bigQuery, TableId.of(projectId, dataset, table))
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
                    put("version", run.version)
                    put("application", run.application)
                    put("run_ts", run.startedAt.toString())
                    put("is_reference_version", run.isReferenceVersion)
                    run.referenceVersion?.let { put("reference_version", it) }
                    run.testsetVersion?.let { put("testset_version", it) }
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
