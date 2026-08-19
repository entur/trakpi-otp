package org.opentripplanner.trakpi.orchestrator

import org.opentripplanner.trakpi.common.PlannerVersion

/**
 * The lifecycle side of a planner integration: how the `start` and `stop` commands bring a planner
 * build up and take it down. A planner integration supplies an implementation when it can manage the
 * planner process itself. Without one, `start` and `stop` report that orchestration is not configured.
 */
interface PlannerOrchestrator {
    /**
     * Start planner [version], returning once it is ready to serve. [args] is opaque and
     * trakpi passes it through untouched to let the implementation interpret it.
     * May be null when the caller supplies no arguments.
     *
     * TODO: We may consider returning before startup is complete, instead returning once we have entered
     *  some kind of starting-up state. This is because startup may take a long time.
     */
    fun start(version: PlannerVersion, args: String?)

    /** Stop the running planner [version]. */
    fun stop(version: PlannerVersion)
}
