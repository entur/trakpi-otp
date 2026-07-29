package org.opentripplanner.trakpi.tester

/** Logs progress to standard out as a separate line per report. */
internal class LoggingProgressReporter : ProgressReporter {
    override fun report(itemsProcessed: Int, total: Int) {
        val percentComplete = itemsProcessed * 100 / total
        println("$percentComplete% complete. $itemsProcessed/$total requests processed.")
    }
}
