package org.opentripplanner.trakpi.otp.testset

import org.opentripplanner.trakpi.testset.Request
import org.opentripplanner.trakpi.testset.RequestCodec

/** Decodes stored requests into [OtpRequest]s and back, via [OtpRequest.parse] and [OtpRequest.toRequest]. */
object OtpRequestCodec : RequestCodec<OtpRequest> {
    override fun deserialize(request: Request): OtpRequest = OtpRequest.parse(request)

    override fun serialize(request: OtpRequest): Request = request.toRequest()
}
