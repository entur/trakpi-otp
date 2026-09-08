package org.opentripplanner.trakpi.tester.spi

/**
 * Renders a planner's response comparison for reading when drilling down into a run. Given a candidate
 * response and its reference, it produces human-readable lines (e.g. an itinerary diff).
 */
interface DrillDownRenderer {
    /**
     * Human-readable lines comparing [candidate] against its [reference] response for the same request, in
     * whatever form makes sense for the planner. [reference] is null when the run had no reference version
     * or none was archived, in which case the candidate is rendered on its own.
     */
    fun render(candidate: TravelPlannerResponse, reference: TravelPlannerResponse?): List<String>
}
