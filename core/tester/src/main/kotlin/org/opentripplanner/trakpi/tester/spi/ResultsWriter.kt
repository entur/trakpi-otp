package org.opentripplanner.trakpi.tester.spi

/** Persists test results. */
interface ResultsWriter {
    fun store(run: RunMetadata, result: TestCaseResult)
}
