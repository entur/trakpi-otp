package org.opentripplanner.trakpi.otp

import java.nio.file.Path
import org.opentripplanner.trakpi.otp.kpi.DepartureCountKPICalculator
import org.opentripplanner.trakpi.otp.kpi.FastestItineraryKPICalculator
import org.opentripplanner.trakpi.otp.kpi.ItineraryCountKPICalculator
import org.opentripplanner.trakpi.otp.kpi.ItineraryCountMatchesReferenceKPICalculator
import org.opentripplanner.trakpi.otp.kpi.MinTransfersKPICalculator
import org.opentripplanner.trakpi.otp.kpi.RoutingTimeKPICalculator
import org.opentripplanner.trakpi.otp.testset.OtpRequestCodec
import org.opentripplanner.trakpi.otp.testset.transforms.EnsureKpiFields
import org.opentripplanner.trakpi.otp.testset.transforms.ObfuscateCoordinates
import org.opentripplanner.trakpi.otp.testset.transforms.OtpStationSnapper
import org.opentripplanner.trakpi.TesterConfig
import org.opentripplanner.trakpi.TestsetConfig
import org.opentripplanner.trakpi.runTrakpi
import org.opentripplanner.trakpi.storage.bigquery.BigQueryResultsWriter
import org.opentripplanner.trakpi.storage.bigquery.BigQueryTestsetSource
import org.opentripplanner.trakpi.storage.bigquery.RequestEnvironment
import org.opentripplanner.trakpi.storage.file.FileResultsWriter
import org.opentripplanner.trakpi.storage.file.FileTestsetStore
import org.opentripplanner.trakpi.storage.gcs.GcsRequestFileLoader
import org.opentripplanner.trakpi.storage.gcs.GcsResultsReader
import org.opentripplanner.trakpi.storage.gcs.GcsResultsWriter
import org.opentripplanner.trakpi.storage.gcs.GcsTestsetStore
import org.opentripplanner.trakpi.tester.FanOutResultsWriter
import org.opentripplanner.trakpi.tester.spi.RequestFileLoader
import org.opentripplanner.trakpi.tester.spi.ResultsReader
import org.opentripplanner.trakpi.tester.spi.ResultsWriter
import org.opentripplanner.trakpi.testset.TestsetSource
import org.opentripplanner.trakpi.testset.TestsetStore

private const val OTP_DEV_ENDPOINT = "https://api.dev.entur.io/journey-planner/v3/graphql"

// Wires the OTP implementations of the trakpi SPI to the CLI. KPI metrics stream into BigQuery when
// TRAKPI_BQ_PROJECT is set (the Entur nightly), otherwise they are written as JSON files under
// TRAKPI_RESULTS_DIR (local runs and the OSS reference). When TRAKPI_GCS_BUCKET is set, each result's
// raw request/response is additionally archived to that bucket so later runs can compare against it.
fun main(args: Array<String>) {
    // The KPIs drive both scoring and which fields the testset must request, so they are declared once
    // and shared: adding a KPI here is enough to pull the fields it reads into prepared requests.
    val kpiCalculators =
        listOf(
            ItineraryCountKPICalculator(),
            RoutingTimeKPICalculator(),
            FastestItineraryKPICalculator(),
            MinTransfersKPICalculator(),
            DepartureCountKPICalculator(),
        )
    // When TRAKPI_OTP_IMAGE_REPO is set, start/stop manage an in-cluster OTP pod and test targets it;
    // otherwise orchestration is off and test uses OTP_ENDPOINT, falling back to the dev API.
    val orchestrator = OtpOrchestrator.createOrNull()
    val endpoint = orchestrator?.endpoint() ?: System.getenv("OTP_ENDPOINT") ?: OTP_DEV_ENDPOINT
    runTrakpi(
        args,
        application = "otp",
        orchestrator = orchestrator,
        tester =
            TesterConfig(
                requestLoader = OtpRequestLoader(),
                travelPlanner = OTPTravelPlanner(endpoint, clientName = "entur-trakpi-dev"),
                kpiCalculators = kpiCalculators,
                resultsWriter = resultsWriter(),
                comparativeKpiCalculators = listOf(ItineraryCountMatchesReferenceKPICalculator()),
                resultsReader = resultsReader(),
                requestFileLoader = requestFileLoader(),
            ),
        testset =
            TestsetConfig(
                api = "transmodel",
                source = testsetSource(),
                codec = OtpRequestCodec,
                transforms =
                    listOf(
                        ObfuscateCoordinates(OtpStationSnapper(OTP_DEV_ENDPOINT, clientName = "entur-trakpi-dev")),
                        EnsureKpiFields(kpiCalculators),
                    ),
                store = testsetStore(),
            ),
    )
}

/**
 * The source of raw requests for `testset prepare`
 */
private fun testsetSource(): TestsetSource? {
    val environment = System.getenv("TRAKPI_REQUESTS_ENV") ?: return null
    return BigQueryTestsetSource.create(
        environment = RequestEnvironment.valueOf(environment.uppercase()),
        sampleSize = System.getenv("TRAKPI_REQUESTS_SAMPLE_SIZE")?.toInt() ?: 1000,
    )
}

/**
 * Where `test` reads its request files from: the testset in GCS when TRAKPI_GCS_BUCKET is set,
 * otherwise null so the CLI falls back to local filesystem.
 */
private fun requestFileLoader(): RequestFileLoader? {
    val bucket = System.getenv("TRAKPI_GCS_BUCKET") ?: return null
    return GcsRequestFileLoader.create(bucket, api = "transmodel")
}

/** Where prepared testsets are stored: a GCS bucket when TRAKPI_GCS_BUCKET is set, otherwise local files. */
private fun testsetStore(): TestsetStore {
    System.getenv("TRAKPI_GCS_BUCKET")?.let { return GcsTestsetStore.create(it) }
    return FileTestsetStore(Path.of(System.getenv("TRAKPI_TESTSET_DIR") ?: "testsets"))
}

private fun resultsReader(): ResultsReader? {
    val bucket = System.getenv("TRAKPI_GCS_BUCKET") ?: return null
    return GcsResultsReader.create(bucket)
}

private fun resultsWriter(): ResultsWriter {
    val writers = buildList {
        val bqProject = System.getenv("TRAKPI_BQ_PROJECT")
        if (bqProject != null) {
            add(
                BigQueryResultsWriter.create(
                    projectId = bqProject,
                    dataset = System.getenv("TRAKPI_BQ_DATASET") ?: "kpi_tracking",
                    table = System.getenv("TRAKPI_BQ_TABLE") ?: "kpi_metrics_v1",
                )
            )
        } else {
            add(FileResultsWriter(Path.of(System.getenv("TRAKPI_RESULTS_DIR") ?: "results")))
        }
        System.getenv("TRAKPI_GCS_BUCKET")?.let { add(GcsResultsWriter.create(it)) }
    }
    return FanOutResultsWriter(writers)
}
