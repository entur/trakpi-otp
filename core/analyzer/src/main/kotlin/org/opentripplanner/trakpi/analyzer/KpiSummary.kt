package org.opentripplanner.trakpi.analyzer

import kotlin.math.ceil
import kotlin.math.sqrt

/** Aggregate statistics for one KPI across all requests in a single run. */
data class KpiSummary(
    val name: String,
    val count: Int,
    val zeroCount: Int,
    val mean: Double,
    val stddev: Double,
    val median: Double,
    val p95: Double,
    val min: Double,
    val max: Double,
) {
    companion object {
        /** Summarizes the [values] of the KPI named [name]. An empty list yields zeroes. */
        fun of(name: String, values: List<Double>): KpiSummary {
            if (values.isEmpty()) return KpiSummary(name, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
            val sorted = values.sorted()
            val mean = values.average()
            return KpiSummary(
                name = name,
                count = values.size,
                zeroCount = values.count { it == 0.0 },
                mean = mean,
                stddev = stddev(sorted, mean),
                median = percentile(sorted, 50.0),
                p95 = percentile(sorted, 95.0),
                min = sorted.first(),
                max = sorted.last(),
            )
        }

        /** Compute standard deviation with Bessel's correction */
        private fun stddev(values: List<Double>, mean: Double): Double {
            // Compute sample variance with Bessel's correction (i.e. we use n - 1 instead of n in the formula).
            // This is undefiend for a single value, so in that case, we return 0.
            if (values.size <= 1) {
                return 0.0;
            }
            val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
            return sqrt(variance);
        }

        /** Nearest-rank percentile over an already-sorted, non-empty list. */
        private fun percentile(sorted: List<Double>, p: Double): Double {
            val rank = ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
            return sorted[rank - 1]
        }
    }
}
