package org.opentripplanner.trakpi.tester.spi

/** Persists test results. */
interface ResultsWriter {
    fun store(run: RunMetadata, result: TestCaseResult)

    /**
     * Called once [phase] of [run] has stored all its results. It is not called when the phase fails or is
     * killed part-way.
     */
    fun completeRun(run: RunMetadata, phase: RunPhase) {}
}
