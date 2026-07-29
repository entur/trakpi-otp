package org.opentripplanner.trakpi.tester

internal interface ProgressReporter {
    /**
     * Reports that [itemsProcessed] out of [total] items have been processed.
     *
     * Does not support total = 0. The behavior for total=0 is undefined.
     */
    fun report(itemsProcessed: Int, total: Int)
}
