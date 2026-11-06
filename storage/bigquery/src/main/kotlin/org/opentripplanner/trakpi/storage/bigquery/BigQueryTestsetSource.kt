package org.opentripplanner.trakpi.storage.bigquery

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.QueryJobConfiguration
import com.google.cloud.bigquery.QueryParameterValue
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.opentripplanner.trakpi.testset.Request
import org.opentripplanner.trakpi.testset.TestsetSource

/** The Entur environment we fetch logged requests from. [requestLogTable] is the fully qualified BigQuery table holding its raw request log. */
enum class RequestEnvironment(val requestLogTable: String) {
    PRD("ent-deneir-prd.journey_planner_v3.journey_planner_v3_raw_requests"),
    TST("ent-deneir-tst.journey_planner_v3.journey_planner_v3_raw_requests"),
}

/**
 * Sources testset requests from an Entur request log in BigQuery: a random sample of up to
 * [sampleSize] requests spread evenly over the 7 most recent complete days in Europe/Oslo, from the same
 * weekday a week ago up to and including yesterday. Each request is keyed by its external correlation id.
 * Each hour contributes the same number of requests, so the testset covers every weekday and the whole day's
 * traffic pattern. An hour with fewer logged requests than its share contributes all it has.
 *
 * A logged request that leaves its search time out searched at the moment it was sent. [writeSentTime] gets each
 * body with that moment and returns the body with the time written into it, wherever the planner's request
 * format has a place for it. By default bodies are kept as logged.
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
    private val writeSentTime: (body: String, sentAt: Instant) -> String = { body, _ -> body },
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
                .addNamedParameter("days", QueryParameterValue.int64(DAYS.toLong()))
                .addNamedParameter("perHour", QueryParameterValue.int64(perHour.toLong()))
                .build()
        return bigQuery.query(config).iterateAll().map { row ->
            val sentAt = Instant.EPOCH.plus(row.get("sent_at").timestampValue, ChronoUnit.MICROS)
            Request(id = row.get("id").stringValue, body = writeSentTime(row.get("body").stringValue, sentAt))
        }
    }

    companion object {
        /** The number of complete days the sample is spread over. */
        private const val DAYS = 7

        /** The number of hours the sample is spread over; a week with a daylight-saving change has one hour more or less. */
        private const val HOURS = DAYS * 24

        /** The time zone whose calendar days bound the sample. */
        private const val ZONE = "Europe/Oslo"

        /** The default share of storage blocks the query reads. */
        const val DEFAULT_SAMPLE_PERCENT = 1.0

        // Keeps one random request per correlation id, then ranks the requests randomly within each hour,
        // keeps the first @perHour of each, and orders by rank before hour so the result interleaves the hours.
        // Deduplicating before ranking lets every kept row count towards the hour's share. The window runs from
        // Oslo midnight @days days ago up to Oslo midnight today.
        internal fun query(requestLogTable: String, samplePercent: Double) =
            """
            WITH unique_ids AS (
              SELECT
                JSON_VALUE(attributes["X-Big-Daddy-External-Correlation-Id"]) AS id,
                DATA AS body,
                publish_time AS sent_at,
                TIMESTAMP_TRUNC(publish_time, HOUR) AS hour
              FROM `$requestLogTable` TABLESAMPLE SYSTEM (${samplePercentLiteral(samplePercent)} PERCENT)
              WHERE publish_time >= TIMESTAMP(DATE_SUB(CURRENT_DATE('$ZONE'), INTERVAL @days DAY), '$ZONE')
                AND publish_time < TIMESTAMP(CURRENT_DATE('$ZONE'), '$ZONE')
                AND DATA IS NOT NULL
                AND JSON_VALUE(attributes["X-Big-Daddy-External-Correlation-Id"]) IS NOT NULL
              QUALIFY ROW_NUMBER() OVER (PARTITION BY JSON_VALUE(attributes["X-Big-Daddy-External-Correlation-Id"]) ORDER BY RAND()) = 1
            ),
            ranked AS (
              SELECT id, body, sent_at, hour, ROW_NUMBER() OVER (PARTITION BY hour ORDER BY RAND()) AS rank
              FROM unique_ids
            )
            SELECT id, body, sent_at
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
        fun create(
            environment: RequestEnvironment,
            sampleSize: Int,
            samplePercent: Double = DEFAULT_SAMPLE_PERCENT,
            writeSentTime: (body: String, sentAt: Instant) -> String = { body, _ -> body },
        ): BigQueryTestsetSource =
            BigQueryTestsetSource(BigQueryOptions.getDefaultInstance().service, environment.requestLogTable, sampleSize, samplePercent, writeSentTime)
    }
}
