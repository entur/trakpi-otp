package org.opentripplanner.trakpi.orchestrator

import org.opentripplanner.trakpi.common.PlannerVersion

/**
 * The lifecycle side of a planner integration, with ability to `start` and `stop` a planner, and see its status.
 */
interface PlannerOrchestrator {
    /**
     * Start planner [version]. [args] is opaque and trakpi passes it through untouched to let the
     * implementation interpret it. May be null when the caller supplies no arguments.
     */
    fun start(version: PlannerVersion, args: String?)

    /** Stop the running planner [version]. */
    fun stop(version: PlannerVersion)

    /** Report where planner [version] is in its lifecycle, so a caller can poll for readiness. */
    fun status(version: PlannerVersion): PlannerStatus
}
