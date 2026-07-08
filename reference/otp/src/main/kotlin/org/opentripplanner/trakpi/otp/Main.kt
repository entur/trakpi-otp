package org.opentripplanner.trakpi.otp

import java.nio.file.Path
import org.opentripplanner.trakpi.runTrakpi
import org.opentripplanner.trakpi.storage.bigquery.BigQueryResultsStorage
import org.opentripplanner.trakpi.storage.file.FileResultsLoader
import org.opentripplanner.trakpi.storage.file.FileResultsStorage
import org.opentripplanner.trakpi.tester.spi.ResultsStorage

private const val OTP_DEV_ENDPOINT = "https://api.dev.entur.io/journey-planner/v3/graphql"

// Wires the OTP implementations of the trakpi SPI to the CLI. Results stream straight into BigQuery
// when TRAKPI_BQ_PROJECT is set (the Entur nightly), otherwise they are written as JSON files under
// TRAKPI_RESULTS_DIR (local runs and the OSS reference).
fun main(args: Array<String>) =
    runTrakpi(
        args,
        requestLoader = OtpRequestLoader(),
        travelPlanner = OTPTravelPlanner(OTP_DEV_ENDPOINT, clientName = "entur-trakpi-dev"),
        kpiCalculators =
            listOf(
                ItineraryCountKPICalculator(),
                RoutingTimeKPICalculator(),
                FastestItineraryKPICalculator(),
                MinTransfersKPICalculator(),
            ),
        resultsStorage = resultsStorage(),
        resultsLoader = FileResultsLoader(),
    )

private fun resultsStorage(): ResultsStorage {
    val bqProject = System.getenv("TRAKPI_BQ_PROJECT")
    return if (bqProject != null) {
        BigQueryResultsStorage.create(
            projectId = bqProject,
            dataset = System.getenv("TRAKPI_BQ_DATASET") ?: "kpi_tracking",
            table = System.getenv("TRAKPI_BQ_TABLE") ?: "kpi_metrics_v1",
        )
    } else {
        FileResultsStorage(Path.of(System.getenv("TRAKPI_RESULTS_DIR") ?: "results"))
    }
}
