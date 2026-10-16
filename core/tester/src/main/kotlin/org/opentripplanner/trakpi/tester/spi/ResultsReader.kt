package org.opentripplanner.trakpi.tester.spi

import org.opentripplanner.trakpi.common.PlannerVersion
import org.opentripplanner.trakpi.common.TestsetVersion

/**
 * One request's archived responses from the two runs being compared. [reference] is null when the reference run
 * has no response for the request.
 */
data class ResponsePair(val requestId: String, val subject: TravelPlannerResponse, val reference: TravelPlannerResponse?)

/**
 * Reads stored results for comparison.
 */
interface ResultsReader {
    /**
     * The archived responses of the planner [subject] for [testsetVersion], each paired with the [reference]
     * planner's response to the same request. The sequence is lazy and holds a bounded number of pairs at a
     * time, however large the testset is, so a caller can consume it one pair at a time. Pairs come in no
     * particular order. It is empty when [subject] has no responses stored for [testsetVersion].
     */
    fun responsePairs(subject: PlannerVersion, reference: PlannerVersion, testsetVersion: TestsetVersion): Sequence<ResponsePair>

    /**
     * The single archived response of [version] for [testsetVersion] and [requestId], or null when none
     * is stored.
     */
    fun response(version: PlannerVersion, testsetVersion: TestsetVersion, requestId: String): TravelPlannerResponse?
}
