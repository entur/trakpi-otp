package org.opentripplanner.trakpi.orchestrator

/**
 * Where a planner build is in its lifecycle, as reported by [PlannerOrchestrator.status]. Lets a caller
 * poll for readiness itself rather than block inside [PlannerOrchestrator.start].
 */
enum class PlannerStatus {
    /** No instance of this version exists — it was never started, or has been stopped. */
    NOT_RUNNING,
    /** An instance exists and is coming up, but does not yet answer requests. */
    STARTING,
    /** The instance is up and serving requests. */
    READY,
    /** The instance failed to start or terminated unexpectedly. */
    FAILED,
}
