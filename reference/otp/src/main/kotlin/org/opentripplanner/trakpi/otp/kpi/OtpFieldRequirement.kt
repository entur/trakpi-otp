package org.opentripplanner.trakpi.otp.kpi

import org.opentripplanner.trakpi.otp.graphql.GraphQlUtil
import org.opentripplanner.trakpi.otp.testset.OtpRequest

/**
 * A requirement for certain fields to be present in an OTP response. A calculator that reads response fields
 * declares them here, and `testset prepare` merges the [requiredFields] into prepared requests (see
 * EnsureKpiFields) so they are present when the calculator runs. Carried by both single-response
 * [OtpKPICalculator]s and [OtpComparativeKPICalculator]s.
 */
interface OtpFieldRequirement {
    /** The [RequiredFields] read to compute the KPI. */
    val requiredFields: RequiredFields?
        get() = null

    /** Returns [request] with [requiredFields] merged, or unchanged when none is declared. */
    fun ensureRequiredFields(request: OtpRequest): OtpRequest =
        requiredFields?.let { fields -> request.mapAst { GraphQlUtil.mergeFields(it, fields.rootFields, fields.selection) } }
            ?: request
}
