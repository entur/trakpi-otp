package org.opentripplanner.trakpi.otp.testset.transforms

import graphql.language.Argument
import graphql.language.ArrayValue
import graphql.language.Document
import graphql.language.Field
import graphql.language.FloatValue
import graphql.language.InlineFragment
import graphql.language.IntValue
import graphql.language.ObjectValue
import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import graphql.language.Value
import java.math.BigDecimal
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.opentripplanner.trakpi.otp.testset.OtpRequest
import org.opentripplanner.trakpi.testset.RequestTransform

/**
 * Obfuscates every `latitude`/`longitude` pair in a request by snapping it to the nearest stop place
 * via [snapper] and then displacing it a random distance (up to [maxMeters]) in a random
 * direction. Pairs are obfuscated both inline in the query and in the GraphQL variables. Coordinates are
 * rounded to 6 decimals. A pair is any scope (a field's arguments, or an object) holding both a
 * `latitude` and a `longitude` number.
 */
class ObfuscateCoordinates(
    private val snapper: CoordinateSnapper,
    private val random: Random = Random.Default,
    private val maxMeters: Double = 500.0,
) : RequestTransform<OtpRequest> {
    override fun apply(request: OtpRequest): OtpRequest = request.mapAst(::obfuscateDocument).mapVariables(::obfuscateVariables)

    private fun obfuscateDocument(document: Document): Document {
        val definitions =
            document.definitions.map { def ->
                if (def is OperationDefinition) def.transform { it.selectionSet(obfuscateSelections(def.selectionSet)) } else def
            }
        return document.transform { it.definitions(definitions) }
    }

    private fun obfuscateSelections(selectionSet: SelectionSet): SelectionSet {
        val selections =
            selectionSet.selections.map { selection ->
                when (selection) {
                    is Field ->
                        selection.transform { b ->
                            b.arguments(obfuscateArguments(selection.arguments))
                            selection.selectionSet?.let { b.selectionSet(obfuscateSelections(it)) }
                        }
                    is InlineFragment -> selection.transform { it.selectionSet(obfuscateSelections(selection.selectionSet)) }
                    else -> selection
                }
            }
        return selectionSet.transform { it.selections(selections) }
    }

    /** Obfuscates a `latitude`/`longitude` argument pair on a field (e.g. `nearest`), after recursing into object arguments. */
    private fun obfuscateArguments(arguments: List<Argument>): List<Argument> {
        val recursed = arguments.map { arg -> arg.transform { it.value(obfuscateValue(arg.value)) } }
        val point = coordinateOf(recursed.associate { it.name to it.value }) ?: return recursed
        val moved = obfuscate(point)
        return recursed.map { arg ->
            when (arg.name) {
                "latitude" -> arg.transform { it.value(floatValue(moved.latitude)) }
                "longitude" -> arg.transform { it.value(floatValue(moved.longitude)) }
                else -> arg
            }
        }
    }

    /** Obfuscates a `latitude`/`longitude` field pair inside an object (e.g. a `coordinates` object), after recursing. */
    private fun obfuscateValue(value: Value<*>): Value<*> =
        when (value) {
            is ObjectValue -> {
                val recursed = value.objectFields.map { field -> field.transform { it.value(obfuscateValue(field.value)) } }
                val point = coordinateOf(recursed.associate { it.name to it.value })
                val fields =
                    if (point == null) {
                        recursed
                    } else {
                        val moved = obfuscate(point)
                        recursed.map { field ->
                            when (field.name) {
                                "latitude" -> field.transform { it.value(floatValue(moved.latitude)) }
                                "longitude" -> field.transform { it.value(floatValue(moved.longitude)) }
                                else -> field
                            }
                        }
                    }
                value.transform { it.objectFields(fields) }
            }
            is ArrayValue -> value.transform { it.values(value.values.map(::obfuscateValue)) }
            else -> value
        }

    private fun coordinateOf(byName: Map<String, Value<*>>): Coordinate? {
        val latitude = byName["latitude"]?.let(::asDouble)
        val longitude = byName["longitude"]?.let(::asDouble)
        return if (latitude != null && longitude != null) Coordinate(latitude, longitude) else null
    }

    private fun obfuscateVariables(variables: JsonElement?): JsonElement? = variables?.let(::obfuscateJson)

    private fun obfuscateJson(element: JsonElement): JsonElement =
        when (element) {
            is JsonObject -> {
                val recursed = JsonObject(element.mapValues { obfuscateJson(it.value) })
                val latitude = (recursed["latitude"] as? JsonPrimitive)?.doubleOrNull
                val longitude = (recursed["longitude"] as? JsonPrimitive)?.doubleOrNull
                if (latitude == null || longitude == null) {
                    recursed
                } else {
                    val moved = obfuscate(Coordinate(latitude, longitude))
                    JsonObject(
                        recursed.toMutableMap().apply {
                            put("latitude", JsonPrimitive(round(moved.latitude)))
                            put("longitude", JsonPrimitive(round(moved.longitude)))
                        }
                    )
                }
            }
            is JsonArray -> JsonArray(element.map(::obfuscateJson))
            else -> element
        }

    private fun obfuscate(point: Coordinate): Coordinate {
        val station = snapper.snap(point)
        val distance = random.nextDouble() * maxMeters
        val bearing = random.nextDouble() * 2 * Math.PI
        val dLat = distance * cos(bearing) / METERS_PER_DEGREE
        val dLon = distance * sin(bearing) / (METERS_PER_DEGREE * cos(Math.toRadians(station.latitude)))
        return Coordinate(station.latitude + dLat, station.longitude + dLon)
    }

    private fun asDouble(value: Value<*>): Double? =
        when (value) {
            is FloatValue -> value.value.toDouble()
            is IntValue -> value.value.toDouble()
            else -> null
        }

    private fun floatValue(value: Double): FloatValue = FloatValue.newFloatValue(BigDecimal.valueOf(round(value))).build()

    private fun round(value: Double): Double = (value * 1_000_000).roundToLong() / 1_000_000.0

    private companion object {
        const val METERS_PER_DEGREE = 111_320.0
    }
}
