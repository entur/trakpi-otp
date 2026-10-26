package org.opentripplanner.trakpi.tester.spi

/** Executes a request against a travel planner. */
interface TravelPlanner {
    fun execute(request: TravelPlannerRequest): TravelPlannerResponse
}
