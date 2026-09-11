package org.opentripplanner.trakpi.otp

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * The shape of an OTP request file body: a GraphQL [query] with optional GraphQL [variables]. Parses
 * the two accepted forms (a `{"query":...,"variables":...}` object, or a bare query string) and
 * serializes back, as the object form when there are variables, otherwise as a bare query.
 */
data class OtpRequestBody(val query: String, val variables: JsonElement?) {
    fun serialize(): String =
        if (variables == null) query
        else Json.encodeToString(JsonObject.serializer(), buildJsonObject { put(JSON_QUERY_FIELD, query); put(JSON_VARIABLES_FIELD, variables) })

    companion object {
        /** The JSON key for the GraphQL query string in a `{"query":...,"variables":...}` envelope. */
        private const val JSON_QUERY_FIELD = "query"
        /** The JSON key for the GraphQL variables object in a `{"query":...,"variables":...}` envelope. */
        private const val JSON_VARIABLES_FIELD = "variables"

        private val jsonParser = Json { isLenient = true; ignoreUnknownKeys = true; allowTrailingComma = true }

        fun parse(body: String): OtpRequestBody {
            val cleaned = body.trimStart('\uFEFF') // strip BOM if present
            return try {
                val obj = jsonParser.parseToJsonElement(cleaned).jsonObject
                val query = (obj[JSON_QUERY_FIELD] as? JsonPrimitive)?.contentOrNull
                    ?: throw IllegalArgumentException("JSON request body has no \"$JSON_QUERY_FIELD\" string field")
                val variables = obj[JSON_VARIABLES_FIELD]?.takeUnless { it is JsonNull }
                OtpRequestBody(query, variables)
            } catch (e: SerializationException) {
                if ("\"$JSON_QUERY_FIELD\"" in cleaned) {
                    throw IllegalArgumentException("Request body looks like a JSON envelope but failed to parse as JSON", e)
                }

                // If it's not JSON-formatted with a query field, the entire body is likely a bare GraphQL query.
                // That also means no variables are present as variables can only be passed with a JSON-payload
                OtpRequestBody(cleaned, variables = null)
            }
        }
    }
}
