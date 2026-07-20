package org.opentripplanner.trakpi.otp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The shape of an OTP request file body: a GraphQL [query] with optional GraphQL [variables]. Parses
 * the two accepted forms (a `{"query":...,"variables":...}` object, or a bare query string) and
 * serializes back — as the object form when there are variables, otherwise as a bare query.
 */
data class OtpRequestBody(val query: String, val variables: JsonElement?) {
    fun serialize(): String =
        if (variables == null) query
        else Json.encodeToString(JsonObject.serializer(), buildJsonObject { put("query", query); put("variables", variables) })

    companion object {
        fun parse(body: String): OtpRequestBody {
            val obj = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            val query = obj?.get("query")?.jsonPrimitive?.contentOrNull
            val variables = obj?.get("variables")?.takeUnless { it is JsonNull }
            return if (query != null) OtpRequestBody(query, variables) else OtpRequestBody(body, null)
        }
    }
}
