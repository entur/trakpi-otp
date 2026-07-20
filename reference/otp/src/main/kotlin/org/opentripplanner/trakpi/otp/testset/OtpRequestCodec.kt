package org.opentripplanner.trakpi.otp.testset

import org.opentripplanner.trakpi.otp.graphql.GraphQlUtil
import org.opentripplanner.trakpi.testset.Request
import org.opentripplanner.trakpi.testset.RequestCodec

/**
 * Decodes stored requests into [OtpRequest]s and back, via [OtpRequest.parse] and [OtpRequest.toRequest].
 *
 * Deserialization also inlines fragments, so every later transformation applied after is sees no fragments.
 * See [GraphQlUtil.mergeFields] for why this is required.
 */
object OtpRequestCodec : RequestCodec<OtpRequest> {
    override fun deserialize(request: Request): OtpRequest = OtpRequest.parse(request).mapAst(GraphQlUtil::inlineFragments)

    override fun serialize(request: OtpRequest): Request = request.toRequest()
}
