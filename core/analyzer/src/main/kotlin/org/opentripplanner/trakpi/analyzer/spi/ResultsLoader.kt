package org.opentripplanner.trakpi.analyzer.spi

import org.opentripplanner.trakpi.analyzer.TestRun

/**
 * Loads the stored run history for analysis. [args] are opaque, loader-specific arguments (for the
 * file loader, e.g. `--results-dir results/`), mirroring how `prepare` passes `--plannerargs` to the
 * planner adapter. This keeps trakpi unaware of where or how results are stored.
 */
interface ResultsLoader {
    fun load(args: String?): List<TestRun>
}
