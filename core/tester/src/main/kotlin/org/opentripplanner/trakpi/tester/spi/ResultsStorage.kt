package org.opentripplanner.trakpi.tester.spi

/** Persists test results. */
interface ResultsStorage {
    fun store(run: RunMetadata, result: TestCaseResult)
}
