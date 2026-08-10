package org.opentripplanner.trakpi.storage.bigquery

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.QueryJobConfiguration
import com.google.cloud.bigquery.QueryParameterValue
import org.opentripplanner.trakpi.testset.Request
import org.opentripplanner.trakpi.testset.TestsetSource

/** The Entur environment we fetch logged requests from. [requestLogTable] is the fully qualified BigQuery table holding its raw request log. */
enum class RequestEnvironment(val requestLogTable: String) {
    PRD("ent-deneir-prd.journey_planner_v3.journey_planner_v3_raw_requests"),
    TST("ent-deneir-tst.journey_planner_v3.journey_planner_v3_raw_requests"),
}

/**
 * Sources testset requests from an Entur request log in BigQuery: a random sample of up to
 * [sampleSize] requests from the most recent complete hour, each keyed by its external correlation id.
 * The query targets a single hourly partition, so it scans only that hour.
 *
 * The google cloud service account running this job requires permissions bigquery.dataViewer on [requestLogTable] and bigquery.jobUser.
 *
 * The job runs in and is billed to the caller's default project.
 *
 * Authenticates with Application Default Credentials.
 */
class BigQueryTestsetSource(private val bigQuery: BigQuery, private val requestLogTable: String, private val sampleSize: Int) :
    TestsetSource {
    override fun load(): List<Request> {
        val config =
            QueryJobConfiguration.newBuilder(query(requestLogTable))
                .addNamedParameter("limit", QueryParameterValue.int64(sampleSize.toLong()))
                .build()
        return bigQuery.query(config).iterateAll().map { row -> Request(id = row.get("id").stringValue, body = row.get("body").stringValue) }
    }

    companion object {
        private fun query(requestLogTable: String) =
            """
            SELECT
              JSON_VALUE(attributes["X-Big-Daddy-External-Correlation-Id"]) AS id,
              DATA AS body
            FROM `$requestLogTable`
            WHERE TIMESTAMP_TRUNC(publish_time, HOUR) = TIMESTAMP_TRUNC(TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 HOUR), HOUR)
              AND DATA IS NOT NULL
              AND JSON_VALUE(attributes["X-Big-Daddy-External-Correlation-Id"]) IS NOT NULL
            ORDER BY RAND()
            LIMIT @limit
            """
                .trimIndent()

        /** A source over [environment]'s request log using Application Default Credentials. */
        fun create(environment: RequestEnvironment, sampleSize: Int): BigQueryTestsetSource =
            BigQueryTestsetSource(BigQueryOptions.getDefaultInstance().service, environment.requestLogTable, sampleSize)
    }
}
