package org.opentripplanner.trakpi.otp.kpi

import org.opentripplanner.trakpi.otp.graphql.GraphQlUtil
import org.opentripplanner.trakpi.otp.testset.OtpRequest
import org.opentripplanner.trakpi.tester.spi.KPICalculator

/**
 * A [KPICalculator] for OTP requests. It calculates a KPI based on an [OtpRequest] and defines what fields are
 * required for the calculation.
 */
interface OtpKPICalculator : KPICalculator {
    /**
     * The [RequiredFields] read to compute the KPI.
     */
    val requiredFields: RequiredFields?
        get() = null

    /** Returns [request] with [requiredFields] merged, or unchanged when none is declared. */
    fun ensureRequiredFields(request: OtpRequest): OtpRequest =
        requiredFields?.let { fields -> request.mapAst { GraphQlUtil.mergeFields(it, fields.rootFields, fields.selection) } }
            ?: request
}

/**
 * A GraphQL [selection] (e.g. `{ debugOutput { totalTime } }`) and the [rootFields] to merge it into,
 * e.g. `setof("trip")`
 */
data class RequiredFields(val rootFields: Set<String>, val selection: String)
