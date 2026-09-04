package org.opentripplanner.trakpi.otp.testset.transforms

import org.opentripplanner.trakpi.otp.kpi.OtpFieldRequirement
import org.opentripplanner.trakpi.otp.testset.OtpRequest
import org.opentripplanner.trakpi.testset.RequestTransform

/**
 * Ensures a request asks for the fields required by the [fieldRequirements].
 */
class EnsureKpiFields(private val fieldRequirements: List<OtpFieldRequirement>) : RequestTransform<OtpRequest> {
    override fun apply(request: OtpRequest): OtpRequest =
        fieldRequirements.fold(request) { current, reader -> reader.ensureRequiredFields(current) }
}
