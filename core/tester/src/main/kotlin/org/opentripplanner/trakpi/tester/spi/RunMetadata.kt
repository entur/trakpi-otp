package org.opentripplanner.trakpi.tester.spi

import java.time.Instant
import org.opentripplanner.trakpi.common.PlannerVersion
import org.opentripplanner.trakpi.common.TestsetVersion

/**
 * Identifies a single test run. [runId] groups every result of the run together, independently of
 * where the results are stored; it is derived from the [version] and the run's start time so that
 * a run remains recognizable even if directory names are lost.
 *
 * [application] names the planner under test (e.g. "otp"). [referenceVersion] is the version treated
 * as the baseline for comparison, [isReferenceVersion] records whether this run's [version] is that
 * baseline, and [testsetVersion] identifies the request set the run exercised. These describe the run
 * as a whole but are stored on every result so each row is self-contained.
 */
data class RunMetadata(
    val runId: String,
    val version: PlannerVersion,
    val application: String,
    val referenceVersion: PlannerVersion?,
    val isReferenceVersion: Boolean,
    val testsetVersion: TestsetVersion,
    val startedAt: Instant,
) {
    companion object {
        /** Builds run metadata for [version] started at [startedAt], with a filesystem-safe [runId]. */
        fun create(
            version: PlannerVersion,
            application: String,
            startedAt: Instant,
            testsetVersion: TestsetVersion,
            referenceVersion: PlannerVersion? = null,
        ): RunMetadata =
            RunMetadata(
                runId = "${version.value}_${startedAt.toString().replace(":", "")}",
                version = version,
                application = application,
                referenceVersion = referenceVersion,
                isReferenceVersion = referenceVersion != null && version == referenceVersion,
                testsetVersion = testsetVersion,
                startedAt = startedAt,
            )
    }
}
