package org.opentripplanner.trakpi.orchestrator

/**
 * Manages the lifecycle of a travel planner under test: starting the planner process and stopping
 * it again.
 */
class Orchestrator {
    /**
     * Start the planner process for [version] and return once it is ready to serve.
     *
     * TODO: We may consider returning before startup is complete, instead returning once we have entered
     *  some kind of starting-up state. This is because startup may take a long time.
     */
    fun start(version: String) {
        println("TODO: start $version")
    }

    /** Stop the running planner process for [version]. */
    fun stop(version: String) {
        println("TODO: stop $version")
    }
}
