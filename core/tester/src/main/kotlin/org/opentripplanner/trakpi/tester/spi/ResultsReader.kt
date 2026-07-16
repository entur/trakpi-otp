package org.opentripplanner.trakpi.tester.spi

/**
 * Reads stored results for comparison. The read counterpart to [ResultsWriter].
 */
interface ResultsReader {
    /**
     * The archived responses of the reference build [version] for [testsetVersion], keyed by requestId;
     * empty when that build has none stored. When a build has been exercised more than once, the latest
     * run's responses are what's stored.
     */
    fun referenceResponses(version: String, testsetVersion: String): Map<String, TravelPlannerResponse>
}
