package org.opentripplanner.trakpi.tester.spi

import org.opentripplanner.trakpi.common.PlannerVersion
import org.opentripplanner.trakpi.common.TestsetVersion

/**
 * Reads stored results for comparison.
 */
interface ResultsReader {
    /**
     * The archived responses of the planner [version] for [testsetVersion], each paired with its requestId.
     * The sequence is lazy: implementations read each response from storage only when it is reached, so a
     * caller that consumes it one element at a time holds one response at a time. It is empty when none are
     * stored for the [version]/[testsetVersion] pair.
     */
    fun responses(version: PlannerVersion, testsetVersion: TestsetVersion): Sequence<Pair<String, TravelPlannerResponse>>

    /**
     * The single archived response of [version] for [testsetVersion] and [requestId], or null when none
     * is stored.
     */
    fun response(version: PlannerVersion, testsetVersion: TestsetVersion, requestId: String): TravelPlannerResponse?
}
