package org.opentripplanner.trakpi.tester

import org.opentripplanner.trakpi.tester.spi.ResultsWriter
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult

/**
 * Fans one result out to several [writers] in order, so a run can persist to more than one backend
 * at once (e.g. KPI metrics to BigQuery and the raw request/response to a bucket). Writers run in
 * the given order; a failure in one propagates and skips the rest.
 */
class FanOutResultsWriter(private val writers: List<ResultsWriter>) : ResultsWriter {
    constructor(vararg writers: ResultsWriter) : this(writers.toList())

    override fun store(run: RunMetadata, result: TestCaseResult) {
        writers.forEach { it.store(run, result) }
    }
}
