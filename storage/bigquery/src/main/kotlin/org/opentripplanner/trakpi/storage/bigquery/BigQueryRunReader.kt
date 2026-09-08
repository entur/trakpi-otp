package org.opentripplanner.trakpi.storage.bigquery

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.FieldValue
import com.google.cloud.bigquery.QueryJobConfiguration
import com.google.cloud.bigquery.QueryParameterValue
import org.opentripplanner.trakpi.tester.spi.RunKpiSnapshot
import org.opentripplanner.trakpi.tester.spi.RunReader
import org.opentripplanner.trakpi.tester.spi.RunRef

/**
 * Reads run-level data from the BigQuery KPI table written by [BigQueryResultsWriter]. The table stores one
 * row per (run, request, KPI). This reader groups those rows back into runs.
 * Authenticates with Application Default Credentials.
 */
class BigQueryRunReader(private val bigQuery: BigQuery, private val table: String) : RunReader {

    override fun runsForRequest(requestId: String): List<RunKpiSnapshot> {
        val config =
            QueryJobConfiguration.newBuilder(runsForRequestQuery(table))
                .addNamedParameter("id", QueryParameterValue.string(requestId))
                .build()
        // Rows arrive newest-run-first. A LinkedHashMap keeps that order while grouping a run's KPI rows.
        val byRun = LinkedHashMap<String, Pair<RunRef, MutableMap<String, Double>>>()
        for (row in bigQuery.query(config).iterateAll()) {
            val runId = row.get("run_id").stringValue
            val entry =
                byRun.getOrPut(runId) {
                    RunRef(
                        runId = runId,
                        runTs = row.get("executed").stringValue,
                        version = row.get("version").stringValue,
                        referenceVersion = row.get("reference_version").stringOrNull(),
                        testsetVersion = row.get("testset_version").stringValue,
                    ) to mutableMapOf()
                }
            val kpiName = row.get("kpi_name").stringOrNull()
            val value = row.get("value").doubleOrNull()
            if (kpiName != null && value != null) entry.second[kpiName] = value
        }
        return byRun.values.map { (run, kpis) -> RunKpiSnapshot(run, kpis) }
    }

    override fun resolveRun(runId: String): RunRef? {
        val config =
            QueryJobConfiguration.newBuilder(resolveRunQuery(table))
                .addNamedParameter("run", QueryParameterValue.string(runId))
                .build()
        val row = bigQuery.query(config).iterateAll().firstOrNull() ?: return null
        return RunRef(
            runId = runId,
            runTs = row.get("executed").stringValue,
            version = row.get("version").stringValue,
            referenceVersion = row.get("reference_version").stringOrNull(),
            testsetVersion = row.get("testset_version").stringValue,
        )
    }

    companion object {
        private fun FieldValue.stringOrNull() = if (isNull) null else stringValue

        private fun FieldValue.doubleOrNull() = if (isNull) null else doubleValue

        private fun runsForRequestQuery(table: String) =
            """
            SELECT
              run_id,
              FORMAT_TIMESTAMP('%Y-%m-%dT%H:%M:%SZ', run_ts) AS executed,
              version,
              reference_version,
              testset_version,
              kpi_name,
              value
            FROM `$table`
            WHERE request_id = @id
            ORDER BY run_ts DESC
            """
                .trimIndent()

        private fun resolveRunQuery(table: String) =
            """
            SELECT
              FORMAT_TIMESTAMP('%Y-%m-%dT%H:%M:%SZ', ANY_VALUE(run_ts)) AS executed,
              ANY_VALUE(version) AS version,
              ANY_VALUE(reference_version) AS reference_version,
              ANY_VALUE(testset_version) AS testset_version
            FROM `$table`
            WHERE run_id = @run
            GROUP BY run_id
            LIMIT 1
            """
                .trimIndent()

        /** A reader over `<projectId>.<dataset>.<table>` using Application Default Credentials. */
        fun create(projectId: String, dataset: String, table: String): BigQueryRunReader {
            val bigQuery = BigQueryOptions.newBuilder().setProjectId(projectId).build().service
            return BigQueryRunReader(bigQuery, "$projectId.$dataset.$table")
        }
    }
}
