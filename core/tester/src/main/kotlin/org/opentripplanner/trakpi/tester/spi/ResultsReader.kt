package org.opentripplanner.trakpi.tester.spi

/**
 * Reads stored results.
 */
interface ResultsReader {
    /**
     * The stored [TravelPlannerResponse]s of run [runId] under [testsetVersion].
     * Empty when the run has none stored.
     */
    fun responsesForRun(testsetVersion: String, runId: String): Map<String, TravelPlannerResponse>
}
