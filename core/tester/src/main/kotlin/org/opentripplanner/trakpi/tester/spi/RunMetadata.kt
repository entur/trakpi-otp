package org.opentripplanner.trakpi.tester.spi

import java.time.Instant

/**
 * Identifies a single test run. [runId] groups every result of the run together, independently of
 * where the results are stored; it is derived from the [version] and the run's start time so that
 * a run remains recognizable even if directory names are lost.
 */
data class RunMetadata(val runId: String, val version: String, val startedAt: Instant) {
    companion object {
        /** Builds run metadata for [version] started at [startedAt], with a filesystem-safe [runId]. */
        fun create(version: String, startedAt: Instant): RunMetadata =
            RunMetadata(runId = "${version}_${startedAt.toString().replace(":", "")}", version = version, startedAt = startedAt)
    }
}
