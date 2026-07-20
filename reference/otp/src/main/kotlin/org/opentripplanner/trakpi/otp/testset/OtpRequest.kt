package org.opentripplanner.trakpi.otp.testset

import graphql.language.AstPrinter
import graphql.language.Document
import graphql.parser.Parser
import kotlinx.serialization.json.JsonElement
import org.opentripplanner.trakpi.otp.OtpRequestBody
import org.opentripplanner.trakpi.testset.Request

/**
 * The build-time form of an OTP request: its query parsed to a GraphQL [document] plus any [variables].
 * Transforms edit the AST directly via [mapAst], so a chain of transforms never re-parses — the query is
 * parsed once in [parse] and printed once in [toRequest].
 */
class OtpRequest(
    private val id: String,
    val document: Document,
    private val variables: JsonElement?,
) {
    /** A copy with the query AST replaced by [transform] applied to it. */
    fun mapAst(transform: (Document) -> Document): OtpRequest = OtpRequest(id, transform(document), variables)

    /** A copy with the GraphQL [variables] replaced by [transform] applied to them. */
    fun mapVariables(transform: (JsonElement?) -> JsonElement?): OtpRequest = OtpRequest(id, document, transform(variables))

    /** Renders this request back to its stored [Request] form, printing the query once. */
    fun toRequest(): Request = Request(id, OtpRequestBody(AstPrinter.printAst(document), variables).serialize())

    companion object {
        private val parser = Parser()

        /** Decodes a stored [Request] into an [OtpRequest], parsing its query to an AST once. */
        fun parse(request: Request): OtpRequest {
            val body = OtpRequestBody.parse(request.body)
            return OtpRequest(request.id, parser.parseDocument(body.query), body.variables)
        }
    }
}
