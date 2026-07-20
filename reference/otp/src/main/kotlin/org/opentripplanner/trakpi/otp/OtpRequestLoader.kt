package org.opentripplanner.trakpi.otp

import org.opentripplanner.trakpi.tester.spi.RequestFile
import org.opentripplanner.trakpi.tester.spi.RequestLoader

/**
 * Reads a request file as an OTP GraphQL request.
 */
class OtpRequestLoader : RequestLoader<OtpTravelPlannerRequest> {
    override fun load(file: RequestFile): OtpTravelPlannerRequest {
        val body = OtpRequestBody.parse(file.body)
        return OtpTravelPlannerRequest(body.query, body.variables)
    }
}
