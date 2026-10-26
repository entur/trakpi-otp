package org.opentripplanner.trakpi.tester

import org.opentripplanner.trakpi.common.PlannerVersion
import org.opentripplanner.trakpi.tester.spi.ComparisonCase
import org.opentripplanner.trakpi.tester.spi.ResultsReader
import org.opentripplanner.trakpi.tester.spi.ResultsWriter
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult
import org.opentripplanner.trakpi.tester.spi.kpi.ComparativeKPICalculator
import org.opentripplanner.trakpi.tester.spi.kpi.calculate

/**
 * Scores an already-completed [run] against a [referenceVersion] in a second pass. Reads both versions'
 * archived responses for the run's testset as [ComparisonCase]s, computes each of the
 * [comparativeKpiCalculators] over a case's request and its (subject, reference) responses, and writes the
 * comparative KPIs back under [run], so they land alongside the run's own KPIs, under the same run id.
 *
 * Comparison is decoupled from the test run: each version is tested once, on its own, and any two stored
 * runs are compared afterwards without re-running the planner. Only the subject responses that have a
 * matching reference response are scored. The rest are counted and reported.
 *
 * The comparison cases are consumed one at a time, so memory stays flat however large the testset is. How fast
 * they are read is up to the [resultsReader].
 */
class Comparator(
    private val run: RunMetadata,
    private val referenceVersion: PlannerVersion,
    private val resultsReader: ResultsReader,
    private val comparativeKpiCalculators: List<ComparativeKPICalculator>,
    private val resultsWriter: ResultsWriter,
) {
    fun run() {
        println("Comparing ${run.version} against $referenceVersion on testset ${run.testsetVersion}…")
        var compared = 0
        var scored = 0
        var missingReference = 0
        for ((request, subjectResponse, referenceResponse) in
            resultsReader.comparisonCases(run.version, referenceVersion, run.testsetVersion)) {
            if (++compared % PROGRESS_INTERVAL == 0) println("  compared $compared response(s)")
            if (referenceResponse == null) {
                missingReference++
                continue
            }
            val kpis = comparativeKpiCalculators.mapNotNull { it.calculate(request, subjectResponse, referenceResponse) }
            if (kpis.isNotEmpty()) {
                resultsWriter.store(
                    run,
                    TestCaseResult(
                        requestId = request.id,
                        request = request.raw,
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
        println("Compared $compared response(s); wrote comparative KPIs for $scored request(s)$missingNote.")
    }

    private companion object {
        /** Emit a progress line every this many compared responses. */
        const val PROGRESS_INTERVAL = 1000
    }
}
