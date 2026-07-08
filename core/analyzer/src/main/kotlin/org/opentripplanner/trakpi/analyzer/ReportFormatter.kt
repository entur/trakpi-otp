package org.opentripplanner.trakpi.analyzer

import java.util.Locale

/** Renders analyzer reports as plain-text tables for the command line. */
object ReportFormatter {

    /** One section per KPI, each a time-ordered table of that KPI's per-run aggregates. */
    fun format(report: TrendReport): String {
        if (report.runs.isEmpty()) return "No runs found."
        val kpiNames = report.runs.flatMap { run -> run.kpis.map { it.name } }.distinct().sorted()
        return buildString {
            for (name in kpiNames) {
                appendLine(name)
                appendLine(
                    row("run", "version", "n", "zero", "mean", "stddev", "median", "p95", "min", "max")
                )
                for (run in report.runs) {
                    val k = run.kpis.firstOrNull { it.name == name }
                    appendLine(
                        row(
                            run.timestamp.toString(),
                            run.version,
                            k?.count?.toString() ?: "-",
                            k?.zeroCount?.toString() ?: "-",
                            num(k?.mean),
                            num(k?.stddev),
                            num(k?.median),
                            num(k?.p95),
                            num(k?.min),
                            num(k?.max),
                        )
                    )
                }
                appendLine()
            }
        }.trimEnd()
    }

    /** A KPI-by-KPI comparison of two runs' means, with absolute and percentage change. */
    fun format(report: DiffReport): String = buildString {
        appendLine("baseline: ${report.baseline.version}  (${report.baseline.timestamp}, run ${report.baseline.runId})")
        appendLine("version:  ${report.version.version}  (${report.version.timestamp}, run ${report.version.runId})")
        appendLine()
        appendLine(row("kpi", "baseline", "version", "delta", "change"))
        for (r in report.rows) {
            appendLine(
                row(
                    r.name,
                    num(r.baselineMean),
                    num(r.versionMean),
                    num(r.delta),
                    r.pctChange?.let { String.format(Locale.ROOT, "%+.1f%%", it) } ?: "-",
                )
            )
        }
    }.trimEnd()

    private fun row(vararg cells: String): String {
        val label = cells.first().padEnd(30)
        val rest = cells.drop(1).joinToString("") { it.padStart(12) }
        return label + rest
    }

    private fun num(value: Double?): String = value?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "-"
}
