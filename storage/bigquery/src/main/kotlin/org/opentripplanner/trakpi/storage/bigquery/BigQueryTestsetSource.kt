package org.opentripplanner.trakpi.storage.bigquery

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.QueryJobConfiguration
import com.google.cloud.bigquery.QueryParameterValue
import java.math.BigDecimal
import org.opentripplanner.trakpi.testset.Request
import org.opentripplanner.trakpi.testset.TestsetSource

/** The Entur environment we fetch logged requests from. [requestLogTable] is the fully qualified BigQuery table holding its raw request log. */
enum class RequestEnvironment(val requestLogTable: String) {
    PRD("ent-deneir-prd.journey_planner_v3.journey_planner_v3_raw_requests"),
    TST("ent-deneir-tst.journey_planner_v3.journey_planner_v3_raw_requests"),
}

/**
 * Sources testset requests from an Entur request log in BigQuery: a random sample of up to
 * [sampleSize] requests spread evenly over the 24 most recent complete hours, each keyed by its external
 * correlation id. Each hour contributes the same number of requests, so the testset covers the whole day's
 * traffic pattern. An hour with fewer logged requests than its share contributes all it has.
 *
 * The query reads only [samplePercent] percent of the table's storage blocks, which keeps it cheap.
 *
 * The google cloud service account running this job requires permissions bigquery.dataViewer on [requestLogTable] and bigquery.jobUser.
 *
 * The job runs in and is billed to the caller's default project.
 *
 * Authenticates with Application Default Credentials.
 */
class BigQueryTestsetSource(
    private val bigQuery: BigQuery,
    requestLogTable: String,
    private val sampleSize: Int,
    samplePercent: Double = DEFAULT_SAMPLE_PERCENT,
) : TestsetSource {
    // Validated up front, since the percentage is interpolated into the query text.
    private val query = query(requestLogTable, samplePercent)

    override fun load(): List<Request> {
        // Fetch slightly more than sampleSize so downstream consumers have headroom if some requests are
        // discarded (e.g. unparseable bodies).
        val fetchSize = sampleSize + maxOf(sampleSize / 10, 10)
        val perHour = (fetchSize + HOURS - 1) / HOURS
        val config =
            QueryJobConfiguration.newBuilder(query)
                .addNamedParameter("hours", QueryParameterValue.int64(HOURS.toLong()))
                .addNamedParameter("perHour", QueryParameterValue.int64(perHour.toLong()))
                .build()
        return bigQuery.query(config).iterateAll().map { row -> Request(id = row.get("id").stringValue, body = row.get("body").stringValue) }
    }

    companion object {
        /** The number of complete hours the sample is spread over. */
        private const val HOURS = 24

        /** The default share of storage blocks the query reads. */
        const val DEFAULT_SAMPLE_PERCENT = 1.0

        // Keeps one random request per correlation id, then ranks the requests randomly within each hour,
        // keeps the first @perHour of each, and orders by rank before hour so the result interleaves the hours.
        // Deduplicating before ranking lets every kept row count towards the hour's share.
        internal fun query(requestLogTable: String, samplePercent: Double) =
            """
            WITH unique_ids AS (
              SELECT
                JSON_VALUE(attributes["X-Big-Daddy-External-Correlation-Id"]) AS id,
                DATA AS body,
                TIMESTAMP_TRUNC(publish_time, HOUR) AS hour
              FROM `$requestLogTable` TABLESAMPLE SYSTEM (${samplePercentLiteral(samplePercent)} PERCENT)
              WHERE publish_time >= TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP(), HOUR), INTERVAL @hours HOUR)
                AND publish_time < TIMESTAMP_TRUNC(CURRENT_TIMESTAMP(), HOUR)
                AND DATA IS NOT NULL
                AND JSON_VALUE(attributes["X-Big-Daddy-External-Correlation-Id"]) IS NOT NULL
              QUALIFY ROW_NUMBER() OVER (PARTITION BY JSON_VALUE(attributes["X-Big-Daddy-External-Correlation-Id"]) ORDER BY RAND()) = 1
            ),
            ranked AS (
              SELECT id, body, hour, ROW_NUMBER() OVER (PARTITION BY hour ORDER BY RAND()) AS rank
              FROM unique_ids
            )
            SELECT id, body
            FROM ranked
            WHERE rank <= @perHour
            ORDER BY rank, hour
            """
                .trimIndent()

        /**
         * Renders [samplePercent] as the plain decimal literal TABLESAMPLE takes, e.g. `5` or `0.5`. BigQuery
         * accepts a percentage in (0, 100]; anything else is rejected here rather than by the query.
         */
        internal fun samplePercentLiteral(samplePercent: Double): String {
            require(samplePercent > 0 && samplePercent <= 100) { "The sample percent must be in (0, 100], got $samplePercent" }
            return BigDecimal.valueOf(samplePercent).stripTrailingZeros().toPlainString()
        }

        /** A source over [environment]'s request log using Application Default Credentials. */
        fun create(environment: RequestEnvironment, sampleSize: Int, samplePercent: Double = DEFAULT_SAMPLE_PERCENT): BigQueryTestsetSource =
            BigQueryTestsetSource(BigQueryOptions.getDefaultInstance().service, environment.requestLogTable, sampleSize, samplePercent)
    }
}
