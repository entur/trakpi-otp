package org.opentripplanner.trakpi.tester

/**
 * Tracks progress through a run of [total] requests and reports it once every [reportEveryItems]
 * items processed, plus once at the end. The caller drives it with the running count of items
 * processed, calling [tryReportProgress] after each item. A run of no items never reports.
 */
internal class ProgressTracker(
    private val total: Int,
    private val reportEveryItems: Int = DEFAULT_REPORT_EVERY_ITEMS,
    private val reporter: ProgressReporter = LoggingProgressReporter(),
) {
    init {
        require(reportEveryItems >= 1) { "reportEveryItems must be at least 1, was $reportEveryItems" }
    }

    /** Report progress if [itemsProcessed] lands on a reporting interval or is the final item. */
    fun tryReportProgress(itemsProcessed: Int) {
        if (total > 0 && (itemsProcessed >= total || itemsProcessed % reportEveryItems == 0)) {
            reporter.report(itemsProcessed, total)
        }
    }

    private companion object {
        const val DEFAULT_REPORT_EVERY_ITEMS = 100
    }
}
