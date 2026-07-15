package org.opentripplanner.trakpi.otp

import org.opentripplanner.trakpi.tester.spi.ResultsStorage
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult

/** Writes results to standard output. */
class StdOutResultsStorage : ResultsStorage {
    override fun store(run: RunMetadata, result: TestCaseResult) {
        println("Result for ${result.requestId} (run ${run.runId}): ${result.method}, success=${result.success}")
        for (kpi in result.kpis) {
            println("  ${kpi.name} = ${kpi.value}")
        }
    }
}
