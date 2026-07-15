package org.opentripplanner.trakpi.tester.spi

/**
 * The outcome of running one request: the [method] it targeted, whether it was a [success], the
 * opaque [rawResponse], any implementation-specific [attributes], and the computed [kpis].
 */
data class TestCaseResult(
    val requestId: String,
    val method: String,
    val success: Boolean,
    val rawResponse: String,
    val attributes: Map<String, String>,
    val kpis: List<Kpi>,
)
