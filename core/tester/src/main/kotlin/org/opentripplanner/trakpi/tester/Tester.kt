package org.opentripplanner.trakpi.tester

import org.opentripplanner.trakpi.tester.spi.ComparativeKPICalculator
import org.opentripplanner.trakpi.tester.spi.KPICalculator
import org.opentripplanner.trakpi.tester.spi.RequestFileLoader
import org.opentripplanner.trakpi.tester.spi.RequestLoader
import org.opentripplanner.trakpi.tester.spi.ResultsReader
import org.opentripplanner.trakpi.tester.spi.ResultsWriter
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult
import org.opentripplanner.trakpi.tester.spi.TravelPlanner
import org.opentripplanner.trakpi.tester.spi.TravelPlannerRequest
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/**
 * Runs a test: loads each request file, executes it against the travel planner, computes the KPIs
 * that apply to the response, and stores the result under the given [run]. When [referenceVersion] is
 * set, that build's reference responses are read once via [resultsReader] and each
 * [comparativeKpiCalculators] entry compares this response against the matching reference response.
 */
class Tester<R : TravelPlannerRequest>(
    private val run: RunMetadata,
    private val requestFileLoader: RequestFileLoader,
    private val requestLoader: RequestLoader<R>,
    private val travelPlanner: TravelPlanner<R>,
    private val kpiCalculators: List<KPICalculator>,
    private val resultsWriter: ResultsWriter,
    private val comparativeKpiCalculators: List<ComparativeKPICalculator> = emptyList(),
    private val resultsReader: ResultsReader? = null,
    private val referenceVersion: String? = null,
) {
    fun run() {
        val files = requestFileLoader.loadAll(run.testsetVersion)
        val progress = ProgressTracker(files.size)
        val reference: Map<String, TravelPlannerResponse> =
            if (referenceVersion != null && resultsReader != null)
                resultsReader.responses(referenceVersion, run.testsetVersion)
            else emptyMap()
        files.forEachIndexed { index, file ->
            val request = requestLoader.load(file)
            val response = travelPlanner.execute(request)
            val kpis = kpiCalculators.mapNotNull { it.calculate(response) }
            val referenceResponse = reference[file.id]
            val comparativeKpis =
                if (referenceResponse != null) comparativeKpiCalculators.mapNotNull { it.calculate(response, referenceResponse) }
                else emptyList()
            val allKpis = kpis + comparativeKpis
            resultsWriter.store(
                run,
                TestCaseResult(
                    requestId = file.id,
                    request = file.body,
                    method = response.method,
                    success = response.success,
                    rawResponse = response.raw,
                    attributes = response.attributes,
                    kpis = allKpis,
                ),
            )
            progress.tryReportProgress(itemsProcessed = index + 1)
        }
    }
}
