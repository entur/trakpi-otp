package org.opentripplanner.trakpi.testset

/**
 * A versioned set of requests exercised against a planner. Scoped by [api] and [version].
 */
data class Testset(val api: String, val version: String, val requests: List<Request>)
