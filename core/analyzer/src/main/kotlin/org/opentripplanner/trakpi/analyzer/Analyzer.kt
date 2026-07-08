package org.opentripplanner.trakpi.analyzer

import org.opentripplanner.trakpi.analyzer.spi.ResultsLoader

/**
 * Computes KPI trends and diffs from the run history supplied by a [ResultsLoader].
 */
class Analyzer(private val loader: ResultsLoader) {

    /** Summarizes every run, oldest first, as a KPI trend. */
    fun trend(loaderArgs: String?): TrendReport = TrendReport(loadRuns(loaderArgs).map(::summarize))

    /**
     * Compares the latest run of [version] against the latest run of [baseline]. When [version] is
     * null the most recent run's version is used.
     */
    fun diff(loaderArgs: String?, version: String?, baseline: String): DiffReport {
        val runs = loadRuns(loaderArgs)
        require(runs.isNotEmpty()) { "no runs found to analyze" }
        val target = version ?: runs.last().version
        val versionRun = latestOf(runs, target) ?: throw IllegalArgumentException("no runs found for version '$target'")
        val baselineRun = latestOf(runs, baseline) ?: throw IllegalArgumentException("no runs found for baseline '$baseline'")

        val versionSummary = summarize(versionRun)
        val baselineSummary = summarize(baselineRun)
        val baselineMeans = baselineSummary.kpis.associate { it.name to it.mean }
        val versionMeans = versionSummary.kpis.associate { it.name to it.mean }
        val rows =
            (baselineMeans.keys + versionMeans.keys).sorted().map { name ->
                DiffRow(name, baselineMeans[name] ?: 0.0, versionMeans[name] ?: 0.0)
            }
        return DiffReport(version = versionSummary, baseline = baselineSummary, rows = rows)
    }

    private fun loadRuns(loaderArgs: String?): List<TestRun> = loader.load(loaderArgs).sortedBy { it.timestamp }

    private fun latestOf(runs: List<TestRun>, version: String): TestRun? =
        runs.filter { it.version == version }.maxByOrNull { it.timestamp }

    private fun summarize(run: TestRun): RunSummary {
        val byName = run.results.flatMap { it.kpis }.groupBy { it.name }
        val kpis = byName.entries.sortedBy { it.key }.map { (name, values) -> KpiSummary.of(name, values.map { it.value }) }
        return RunSummary(run.runId, run.version, run.timestamp, kpis)
    }
}
