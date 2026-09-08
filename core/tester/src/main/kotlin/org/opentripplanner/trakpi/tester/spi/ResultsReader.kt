package org.opentripplanner.trakpi.tester.spi

import org.opentripplanner.trakpi.common.PlannerVersion
import org.opentripplanner.trakpi.common.TestsetVersion

/**
 * Reads stored results for comparison.
 */
interface ResultsReader {
    /**
     * The archived responses of the planner [version] for [testsetVersion], keyed by requestId.
     * Returns an empty map when none are stored for the [version]/[testsetVersion] pair.
     */
    fun responses(version: PlannerVersion, testsetVersion: TestsetVersion): Map<String, TravelPlannerResponse>

    /**
     * The single archived response of [version] for [testsetVersion] and [requestId], or null when none
     * is stored.
     */
    fun response(version: PlannerVersion, testsetVersion: TestsetVersion, requestId: String): TravelPlannerResponse?
}
