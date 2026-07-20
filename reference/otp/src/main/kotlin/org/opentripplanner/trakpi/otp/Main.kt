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
import org.opentripplanner.trakpi.storage.file.FileResultsWriter
import org.opentripplanner.trakpi.storage.file.FileTestsetStore
import org.opentripplanner.trakpi.storage.gcs.GcsResultsReader
import org.opentripplanner.trakpi.storage.gcs.GcsResultsWriter
import org.opentripplanner.trakpi.tester.FanOutResultsWriter
import org.opentripplanner.trakpi.tester.spi.ResultsReader
import org.opentripplanner.trakpi.tester.spi.ResultsWriter

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
    runTrakpi(
        args,
        application = "otp",
        tester =
            TesterConfig(
                requestLoader = OtpRequestLoader(),
                travelPlanner = OTPTravelPlanner(OTP_DEV_ENDPOINT, clientName = "entur-trakpi-dev"),
                kpiCalculators = kpiCalculators,
                resultsWriter = resultsWriter(),
                comparativeKpiCalculators = listOf(ItineraryCountMatchesReferenceKPICalculator()),
                resultsReader = resultsReader(),
            ),
        testset =
            TestsetConfig(
                api = "transmodel",
                // source (the BigQuery prod-request fetch) is not wired yet.
                codec = OtpRequestCodec,
                transforms =
                    listOf(
                        ObfuscateCoordinates(OtpStationSnapper(OTP_DEV_ENDPOINT, clientName = "entur-trakpi-dev")),
                        EnsureKpiFields(kpiCalculators),
                    ),
                store = FileTestsetStore(Path.of(System.getenv("TRAKPI_TESTSET_DIR") ?: "testsets")),
            ),
    )
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
