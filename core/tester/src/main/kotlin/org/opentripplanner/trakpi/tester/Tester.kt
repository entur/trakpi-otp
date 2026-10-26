package org.opentripplanner.trakpi.tester

import org.opentripplanner.trakpi.common.mapConcurrently
import org.opentripplanner.trakpi.tester.spi.RequestFileLoader
import org.opentripplanner.trakpi.tester.spi.ResultsWriter
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult
import org.opentripplanner.trakpi.tester.spi.TravelPlanner
import org.opentripplanner.trakpi.tester.spi.TravelPlannerRequest
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse
import org.opentripplanner.trakpi.tester.spi.kpi.KPICalculator
import org.opentripplanner.trakpi.tester.spi.kpi.calculate

/**
 * Runs a test: loads each request file, executes it against the travel planner, computes the KPIs that
 * apply to the request and its response, and stores the result under the given [run]. Comparative scoring
 * against a reference run is a separate second pass (see [Comparator]) so a test run depends only on the
 * planner under test.
 *
 * [concurrency] is the test's load model: how many requests are in flight against the planner at once.
 * Only the planner calls overlap; KPIs are computed and results stored one at a time, in completion order.
 */
class Tester(
    private val run: RunMetadata,
    private val requestFileLoader: RequestFileLoader,
    private val travelPlanner: TravelPlanner,
    private val kpiCalculators: List<KPICalculator>,
    private val resultsWriter: ResultsWriter,
    private val concurrency: Int = 1,
) {
    fun run() {
        val requests = requestFileLoader.loadAll(run.testsetVersion)
        val progress = ProgressTracker(requests.size)
        requests
            .asSequence()
            .mapConcurrently(concurrency) { request -> Executed(request, execute(request)) }
            .forEachIndexed { index, (request, response) ->
                if (response != null) store(request, response)
                progress.tryReportProgress(itemsProcessed = index + 1)
            }
    }

    /** Executes [request]; null when it fails, which is logged. */
    private fun execute(request: TravelPlannerRequest): TravelPlannerResponse? =
        try {
            travelPlanner.execute(request)
        } catch (e: Exception) {
            warn(request, e)
            null
        }

    private fun store(request: TravelPlannerRequest, response: TravelPlannerResponse) {
        try {
            val kpis = kpiCalculators.mapNotNull { it.calculate(request, response) }
            resultsWriter.store(
                run,
                TestCaseResult(
                    requestId = request.id,
                    request = request.raw,
                    method = response.method,
                    success = response.success,
                    rawResponse = response.raw,
                    attributes = response.attributes,
                    kpis = kpis,
                ),
            )
        } catch (e: Exception) {
            warn(request, e)
        }
    }

    private fun warn(request: TravelPlannerRequest, e: Exception) {
        System.err.println("WARNING: request ${request.id} failed (${e.javaClass.simpleName}: ${e.message?.take(150)})")
    }

    private data class Executed(val request: TravelPlannerRequest, val response: TravelPlannerResponse?)
}
