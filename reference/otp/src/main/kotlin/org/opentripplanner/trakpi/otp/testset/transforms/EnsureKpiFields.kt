package org.opentripplanner.trakpi.otp.testset.transforms

import org.opentripplanner.trakpi.otp.kpi.OtpKPICalculator
import org.opentripplanner.trakpi.otp.testset.OtpRequest
import org.opentripplanner.trakpi.testset.RequestTransform

/**
 * Ensures a request asks for the fields every KPI in [kpis] reads, by letting each KPI merge in its own
 * required selection.
 */
class EnsureKpiFields(private val kpis: List<OtpKPICalculator>) : RequestTransform<OtpRequest> {
    override fun apply(request: OtpRequest): OtpRequest =
        kpis.fold(request) { current, kpi -> kpi.ensureRequiredFields(current) }
}

