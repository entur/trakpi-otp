package org.opentripplanner.trakpi.tester

import org.opentripplanner.trakpi.common.mapConcurrently
import org.opentripplanner.trakpi.tester.spi.RequestFile
import org.opentripplanner.trakpi.tester.spi.RequestFileLoader
import org.opentripplanner.trakpi.tester.spi.RequestLoader
import org.opentripplanner.trakpi.tester.spi.ResultsWriter
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult
import org.opentripplanner.trakpi.tester.spi.TravelPlanner
import org.opentripplanner.trakpi.tester.spi.TravelPlannerRequest
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse
import org.opentripplanner.trakpi.tester.spi.kpi.KPICalculator

/**
 * Runs a test: loads each request file, executes it against the travel planner, computes the KPIs that
 * apply to the response, and stores the result under the given [run]. Comparative scoring against a
 * reference run is a separate second pass — see [Comparator] — so a test run depends only on the
 * planner under test.
 *
 * [concurrency] is the test's load model: how many requests are in flight against the planner at once.
 * Only the planner calls overlap; KPIs are computed and results stored one at a time, in completion order.
 */
class Tester<R : TravelPlannerRequest>(
    private val run: RunMetadata,
    private val requestFileLoader: RequestFileLoader,
    private val requestLoader: RequestLoader<R>,
    private val travelPlanner: TravelPlanner<R>,
    private val kpiCalculators: List<KPICalculator>,
    private val resultsWriter: ResultsWriter,
    private val concurrency: Int = 1,
) {
    fun run() {
        val files = requestFileLoader.loadAll(run.testsetVersion)
        val progress = ProgressTracker(files.size)
        files
            .asSequence()
            .mapConcurrently(concurrency) { file -> Executed(file, execute(file)) }
            .forEachIndexed { index, (file, response) ->
                if (response != null) store(file, response)
                progress.tryReportProgress(itemsProcessed = index + 1)
            }
    }

    /** Loads and executes [file]'s request; null when either fails, which is logged. */
    private fun execute(file: RequestFile): TravelPlannerResponse? =
        try {
            travelPlanner.execute(requestLoader.load(file))
        } catch (e: Exception) {
            warn(file, e)
            null
        }

    private fun store(file: RequestFile, response: TravelPlannerResponse) {
        try {
            val kpis = kpiCalculators.mapNotNull { it.calculate(response) }
            resultsWriter.store(
                run,
                TestCaseResult(
                    requestId = file.id,
                    request = file.body,
                    method = response.method,
                    success = response.success,
                    rawResponse = response.raw,
                    attributes = response.attributes,
                    kpis = kpis,
                ),
            )
        } catch (e: Exception) {
            warn(file, e)
        }
    }

    private fun warn(file: RequestFile, e: Exception) {
        System.err.println("WARNING: request ${file.id} failed (${e.javaClass.simpleName}: ${e.message?.take(150)})")
    }

    private data class Executed(val file: RequestFile, val response: TravelPlannerResponse?)
}
