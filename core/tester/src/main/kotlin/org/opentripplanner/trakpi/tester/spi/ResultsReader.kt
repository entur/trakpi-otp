package org.opentripplanner.trakpi.tester.spi

import org.opentripplanner.trakpi.common.PlannerVersion
import org.opentripplanner.trakpi.common.TestsetVersion

/**
 * One test case as the two runs being compared answered it: the archived [request] and each run's archived
 * response to it. [reference] is null when the reference run has no response for the request.
 */
data class ComparisonCase(val request: TravelPlannerRequest, val subject: TravelPlannerResponse, val reference: TravelPlannerResponse?)

/**
 * Reads stored results for comparison.
 */
interface ResultsReader {
    /**
     * A [ComparisonCase] for each archived response of the planner [subject] for [testsetVersion], with the
     * request it answers and the [reference] planner's response to the same request. The sequence is lazy and
     * holds a bounded number of cases at a time, however large the testset is, so a caller can consume it one
     * case at a time. Cases come in no particular order. It is empty when [subject] has no responses stored for
     * [testsetVersion].
     */
    fun comparisonCases(subject: PlannerVersion, reference: PlannerVersion, testsetVersion: TestsetVersion): Sequence<ComparisonCase>

    /**
     * The single archived response of [version] for [testsetVersion] and [requestId], or null when none
     * is stored.
     */
    fun response(version: PlannerVersion, testsetVersion: TestsetVersion, requestId: String): TravelPlannerResponse?
}
