package org.opentripplanner.trakpi.tester.spi

/**
 * A request a travel planner can execute. Each planner defines its own concrete type holding
 * whatever it needs; trakpi treats the request body opaquely.
 */
interface TravelPlannerRequest
