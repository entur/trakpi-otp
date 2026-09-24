package org.opentripplanner.trakpi.tester

import org.opentripplanner.trakpi.tester.spi.RequestFileLoader
import org.opentripplanner.trakpi.tester.spi.RequestLoader
import org.opentripplanner.trakpi.tester.spi.ResultsWriter
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult
import org.opentripplanner.trakpi.tester.spi.TravelPlanner
import org.opentripplanner.trakpi.tester.spi.TravelPlannerRequest
import org.opentripplanner.trakpi.tester.spi.kpi.KPICalculator

/**
 * Runs a test: loads each request file, executes it against the travel planner, computes the KPIs that
 * apply to the response, and stores the result under the given [run]. Comparative scoring against a
 * reference run is a separate second pass — see [Comparator] — so a test run depends only on the
 * planner under test.
 */
class Tester<R : TravelPlannerRequest>(
    private val run: RunMetadata,
    private val requestFileLoader: RequestFileLoader,
    private val requestLoader: RequestLoader<R>,
    private val travelPlanner: TravelPlanner<R>,
    private val kpiCalculators: List<KPICalculator>,
    private val resultsWriter: ResultsWriter,
) {
    fun run() {
        val files = requestFileLoader.loadAll(run.testsetVersion)
        val progress = ProgressTracker(files.size)
        files.forEachIndexed { index, file ->
            try {
                val request = requestLoader.load(file)
                val response = travelPlanner.execute(request)
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
                System.err.println("WARNING: request ${file.id} failed (${e.javaClass.simpleName}: ${e.message?.take(150)})")
            }
            progress.tryReportProgress(itemsProcessed = index + 1)
        }
    }
}
