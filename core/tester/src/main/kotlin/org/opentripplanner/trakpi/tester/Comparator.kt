package org.opentripplanner.trakpi.tester

import org.opentripplanner.trakpi.common.PlannerVersion
import org.opentripplanner.trakpi.tester.spi.ResultsReader
import org.opentripplanner.trakpi.tester.spi.ResultsWriter
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult
import org.opentripplanner.trakpi.tester.spi.kpi.ComparativeKPICalculator

/**
 * Scores an already-completed [run] against a [referenceVersion] in a second pass. Reads both versions'
 * archived responses for the run's testset, pairs them by requestId, computes each of the
 * [comparativeKpiCalculators] over the (subject, reference) pair, and writes the comparative KPIs back
 * under [run] — so they land alongside the run's own KPIs, under the same run id.
 *
 * Comparison is decoupled from the test run: each version is tested once, on its own, and any two stored
 * runs are compared afterwards without re-running the planner. Only the subject responses that have a
 * matching reference response are scored; the rest are counted and reported.
 */
class Comparator(
    private val run: RunMetadata,
    private val referenceVersion: PlannerVersion,
    private val resultsReader: ResultsReader,
    private val comparativeKpiCalculators: List<ComparativeKPICalculator>,
    private val resultsWriter: ResultsWriter,
) {
    fun run() {
        val subject = resultsReader.responses(run.version, run.testsetVersion)
        val reference = resultsReader.responses(referenceVersion, run.testsetVersion)
        println(
            "Comparing ${run.version} against $referenceVersion on testset ${run.testsetVersion}: " +
                "${subject.size} subject and ${reference.size} reference response(s)."
        )
        var scored = 0
        var missingReference = 0
        subject.forEach { (requestId, subjectResponse) ->
            val referenceResponse = reference[requestId]
            if (referenceResponse == null) {
                missingReference++
                return@forEach
            }
            val kpis = comparativeKpiCalculators.mapNotNull { it.calculate(subjectResponse, referenceResponse) }
            if (kpis.isNotEmpty()) {
                resultsWriter.store(
                    run,
                    TestCaseResult(
                        requestId = requestId,
                        // The request body isn't archived with the response; the comparative row needs only
                        // the response's generic facts (method/success/attributes) as dimensions.
                        request = "",
                        method = subjectResponse.method,
                        success = subjectResponse.success,
                        rawResponse = subjectResponse.raw,
                        attributes = subjectResponse.attributes,
                        kpis = kpis,
                    ),
                )
                scored++
            }
        }
        val missingNote = if (missingReference > 0) "; $missingReference had no reference response" else ""
        println("Wrote comparative KPIs for $scored request(s)$missingNote.")
    }
}
