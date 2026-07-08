package org.opentripplanner.trakpi.storage.bigquery

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.TableId
import org.opentripplanner.trakpi.tester.spi.ResultsStorage
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult

/**
 * Streams each result's KPIs into a BigQuery table, one row per KPI value with columns
 * `run_id, version, run_ts, request_id, kpi_name, value`.
 * Assumes the table already exists.
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
         * Maps one result to BigQuery rows, one row per KPI.
         */
        internal fun toRows(run: RunMetadata, result: TestCaseResult): List<Row> =
            result.kpis.map { kpi ->
                Row(
                    insertId = "${run.runId}:${result.requestId}:${kpi.name}",
                    content =
                        mapOf(
                            "run_id" to run.runId,
                            "version" to run.version,
                            "run_ts" to run.startedAt.toString(),
                            "request_id" to result.requestId,
                            "kpi_name" to kpi.name,
                            "value" to kpi.value,
                        ),
                )
            }
    }

    /** An insert id paired with the column values for one BigQuery row. */
    internal data class Row(val insertId: String, val content: Map<String, Any>)
}
