package org.opentripplanner.trakpi.tester

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressTrackerTest {
    private class RecordingReporter : ProgressReporter {
        val reports = mutableListOf<Pair<Int, Int>>()

        override fun report(itemsProcessed: Int, total: Int) {
            reports += itemsProcessed to total
        }
    }

    @Test
    fun `reports once every configured number of items`() {
        val reporter = RecordingReporter()
        val tracker = ProgressTracker(total = 20, reportEveryItems = 2, reporter = reporter)

        for (itemsProcessed in 1..20) tracker.tryReportProgress(itemsProcessed)

        assertEquals(
            listOf(2 to 20, 4 to 20, 6 to 20, 8 to 20, 10 to 20, 12 to 20, 14 to 20, 16 to 20, 18 to 20, 20 to 20),
            reporter.reports,
        )
    }

    @Test
    fun `always reports the final item even when it is not on an interval boundary`() {
        val reporter = RecordingReporter()
        val tracker = ProgressTracker(total = 7, reportEveryItems = 3, reporter = reporter)

        for (itemsProcessed in 1..7) tracker.tryReportProgress(itemsProcessed)

        assertEquals(7 to 7, reporter.reports.last())
    }

    @Test
    fun `an empty run never reports`() {
        val reporter = RecordingReporter()
        val tracker = ProgressTracker(total = 0, reporter = reporter)

        tracker.tryReportProgress(0)

        assertEquals(emptyList(), reporter.reports)
    }

    @Test
    fun `logging reporter prints the percentage and counts`() {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer))
        try {
            LoggingProgressReporter().report(itemsProcessed = 8, total = 20)
        } finally {
            System.setOut(original)
        }

        assertEquals("40% complete. 8/20 requests processed.", buffer.toString().trim())
    }
}
