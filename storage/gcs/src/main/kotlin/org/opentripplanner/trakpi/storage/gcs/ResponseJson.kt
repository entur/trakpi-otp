package org.opentripplanner.trakpi.storage.gcs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/** Serializes a [TravelPlannerResponse] to/from the JSON object stored per result in the GCS archive. */
internal object ResponseJson {
    private val pretty = Json { prettyPrint = true }

    fun serialize(response: TravelPlannerResponse): String =
        pretty.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("raw", response.raw)
                put("success", response.success)
                put("method", response.method)
                putJsonObject("attributes") { response.attributes.forEach { (name, value) -> put(name, value) } }
            },
        )

    fun parse(json: String): TravelPlannerResponse {
        val obj = Json.parseToJsonElement(json).jsonObject
        return TravelPlannerResponse(
            raw = obj.getValue("raw").jsonPrimitive.content,
            success = obj.getValue("success").jsonPrimitive.boolean,
            method = obj.getValue("method").jsonPrimitive.content,
            attributes = (obj["attributes"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap(),
        )
    }
}
