package org.opentripplanner.trakpi.tester.spi

/**
 * Reads stored results for comparison.
 */
interface ResultsReader {
    /**
     * The archived responses of the planner [version] for [testsetVersion], keyed by requestId.
     * Returns an empty map when none are stored for the [version]/[testsetVersion] pair.
     */
    fun responses(version: String, testsetVersion: String): Map<String, TravelPlannerResponse>
}
