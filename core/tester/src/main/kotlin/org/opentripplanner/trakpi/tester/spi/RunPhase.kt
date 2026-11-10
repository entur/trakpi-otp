package org.opentripplanner.trakpi.tester.spi

/**
 * A pass that writes results under a run id. A run's results are complete for a phase only once that phase
 * has finished, so a reader can leave out the results of a phase that is still running or that died.
 */
enum class RunPhase {
    /** Executes the testset against the planner and writes each request's own KPIs. */
    TEST,

    /** Scores the run against a reference run and writes the comparative KPIs. */
    COMPARE,
}
