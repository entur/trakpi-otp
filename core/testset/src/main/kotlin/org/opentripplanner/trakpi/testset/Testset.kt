package org.opentripplanner.trakpi.testset

import org.opentripplanner.trakpi.common.TestsetVersion

/**
 * A versioned set of requests exercised against a planner. Scoped by [api] and [version].
 */
data class Testset(val api: String, val version: TestsetVersion, val requests: List<Request>)
